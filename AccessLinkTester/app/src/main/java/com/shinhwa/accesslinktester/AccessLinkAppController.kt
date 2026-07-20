package com.shinhwa.accesslinktester

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shinhwa.accesslinktester.data.ServiceStore
import com.shinhwa.accesslinktester.model.AccessEvent
import com.shinhwa.accesslinktester.model.AccessEventType
import com.shinhwa.accesslinktester.model.CapturedCard
import com.shinhwa.accesslinktester.model.CardOutcome
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.model.EthernetUiState
import com.shinhwa.accesslinktester.model.RegisteredCard
import com.shinhwa.accesslinktester.model.RegisteredFace
import com.shinhwa.accesslinktester.model.SerialUiState
import com.shinhwa.accesslinktester.model.ServiceSettings
import com.shinhwa.accesslinktester.model.UsbDeviceSnapshot
import com.shinhwa.accesslinktester.model.cardKeyOf
import com.shinhwa.accesslinktester.model.cardNumberOf
import com.shinhwa.accesslinktester.model.evaluateCard
import com.shinhwa.accesslinktester.model.useRelayValue
import com.shinhwa.accesslinktester.model.visibleToUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

private const val RAW_32_CARD_BYTES = 4
private const val RAW_32_BUFFER_TIMEOUT_MS = 300L
private const val RAW_32_STABLE_WINDOW_MS = 2_000L
private const val MAX_EVENTS = 200
private const val MAX_SYSTEM_LOGS = 120
private const val MAX_SERIAL_LOGS = 40
private const val MAX_WIEGAND_TX_LOGS = 20
private const val MAX_RELAY_LOGS = 20
private const val FACE_EMBEDDING_SIZE = 128
private const val FACE_MATCH_L2_THRESHOLD = 10f
private const val FACE_OPEN_COOLDOWN_MS = 8_000L
private const val FACE_FAILURE_LOG_INTERVAL_MS = 3_000L
private const val MANUAL_OPEN_COOLDOWN_MS = 1_000L
private const val FACE_CONFIRMATION_COUNT = 3
private const val FACE_CONFIRMATION_WINDOW_MS = 1_500L

/**
 * 앱 상태 + 출입 서비스 로직의 단일 소유자.
 * - 카드 인증 판정, 문 개방, 카드 등록 대기 모드, 장비 진단 상태를 관리한다.
 * - Android/USB 배선(권한, 연결, 네트워크)은 MainActivity 가 담당하고
 *   결과만 이 컨트롤러에 전달한다. 실제 송신은 [send] 로 위임한다.
 */
class AccessLinkAppController(
    private val store: ServiceStore,
    private val send: (ByteArray) -> Unit
) {
    // --- 서비스 상태 ---
    var doors by mutableStateOf(store.loadDoors())
        private set
    var cards by mutableStateOf(store.loadCards())
        private set
    var faces by mutableStateOf(store.loadFaces())
        private set
    var settings by mutableStateOf(store.loadSettings())
        private set

    val accessEvents = mutableStateListOf<AccessEvent>().apply {
        addAll(store.loadAccessEvents())
    }
    val systemLogs = mutableStateListOf<String>()

    // --- 통신/장비 상태 ---
    var serialState by mutableStateOf(SerialUiState())
        private set
    var ethernetState by mutableStateOf(EthernetUiState())
        private set
    val usbDevices = mutableStateListOf<UsbDeviceSnapshot>()
    var diagnostics by mutableStateOf(DiagnosticsState())
        private set
    val rs232Logs = mutableStateListOf<SerialPortLog>()
    val rs485Logs = mutableStateListOf<SerialPortLog>()
    val wiegandTxLogs = mutableStateListOf<WiegandOutputLog>()
    val relayLogs = mutableStateListOf<RelayControlLog>()

    // --- 관리자 인증 ---
    var adminAuthenticated by mutableStateOf(false)
        private set

    // --- 카드 등록 대기 모드 ---
    var registrationActive by mutableStateOf(false)
        private set
    var capturedCard by mutableStateOf<CapturedCard?>(null)
        private set

    // raw32 카드 수신 버퍼링 상태 (현장 검증 로직 · MainActivity 에서 이식)
    private val raw32Buffer = ArrayDeque<Byte>()
    private var raw32BufferUpdatedAt = 0L
    private var lastRaw32CandidateHex: String? = null
    private var lastRaw32CandidateAt = 0L
    private var lastRaw32CandidateCount = 0
    private var lastFaceOpenAt = 0L
    private var lastFaceFailureLogAt = 0L
    private val lastDoorCommandAt = mutableMapOf<Int, Long>()
    private var lastFaceCandidateName: String? = null
    private var lastFaceCandidateAt = 0L
    private var faceCandidateCount = 0

    // -----------------------------------------------------------------------
    // 관리자 인증
    // -----------------------------------------------------------------------

    fun tryAdminLogin(pin: String): Boolean {
        val ok = AdminConfig.isValid(pin)
        adminAuthenticated = ok
        return ok
    }

    /** 사용자 화면으로 복귀하거나 백그라운드 진입 시 관리자 세션을 닫는다. */
    fun leaveAdmin() {
        adminAuthenticated = false
        cancelCardRegistration()
    }

    // -----------------------------------------------------------------------
    // 문 개방 (사용자/자동 공통) — OPEN 만 존재
    // -----------------------------------------------------------------------

    /**
     * [index] 번 문을 개방한다. door.openSeconds 만큼 릴레이 ON 후 장비가 자동 OFF.
     * @param auto 자동개방(카드 인증)에서 호출되면 true.
     */
    fun openDoor(
        index: Int,
        auto: Boolean = false,
        personName: String? = null,
        cardNumber: String? = null
    ): Boolean {
        val door = doors.firstOrNull { it.index == index } ?: return false
        if (!auto && !door.visibleToUser()) return false
        val now = System.currentTimeMillis()
        if (!auto && now - (lastDoorCommandAt[index] ?: 0L) < MANUAL_OPEN_COOLDOWN_MS) {
            return false
        }
        lastDoorCommandAt[index] = now
        val label = door.name.ifBlank { "Relay${door.index}" }
        val packet = AccessLinkProtocol.relayControl(
            useRelay = door.useRelayValue(),
            outputType = 1,
            time = door.openSeconds
        )
        return if (sendPacket(packet, "$label 개방")) {
            diagnostics = diagnostics.copy(lastRelayHex = packet.toHexString())
            addEvent(
                AccessEventType.OPEN,
                personName = personName,
                cardNumber = cardNumber,
                message = "$label 개방 송신",
                detail = "${door.openSeconds}초"
            )
            true
        } else {
            addEvent(
                AccessEventType.ERROR,
                personName = personName,
                cardNumber = cardNumber,
                message = "$label 개방 실패"
            )
            false
        }
    }

    fun sendRelayControl(useRelay: Int, outputType: Int, time: Int): String {
        if (!adminAuthenticated) return "관리자 인증 필요"
        val relayLabel = when (useRelay) {
            0 -> "Relay 0+1"
            1 -> "Relay 0"
            2 -> "Relay 1"
            else -> return "릴레이 선택 오류"
        }
        val outputLabel = when (outputType) {
            0 -> "OFF"
            1 -> if (time == 0) "지속 ON" else "${time}초 ON"
            else -> return "출력 방식 오류"
        }
        val safeTime = time.coerceIn(0, 99)
        val packet = AccessLinkProtocol.relayControl(
            useRelay = useRelay,
            outputType = outputType,
            time = safeTime
        )

        return if (sendPacket(packet, "$relayLabel $outputLabel")) {
            diagnostics = diagnostics.copy(lastRelayHex = packet.toHexString())
            relayLogs.add(
                0,
                RelayControlLog(
                    relay = relayLabel,
                    command = outputLabel,
                    packetHex = packet.toHexString()
                )
            )
            trim(relayLogs, MAX_RELAY_LOGS)
            "$relayLabel $outputLabel 송신"
        } else {
            "$relayLabel $outputLabel 실패"
        }
    }

    /**
     * 등록 얼굴 인증 성공 후 문 개방.
     * 자동개방 대상 문이 있으면 그 문을 열고, 대상이 없을 때 사용자 노출 문이 1개뿐이면 그 문만 연다.
     * 여러 문이 있는데 대상 지정이 없으면 임의 개방하지 않는다.
     */
    fun openDoorAfterFaceAuth(personName: String): Boolean {
        val visibleDoors = doors.filter { it.visibleToUser() }
        val configuredTargets = visibleDoors.filter { it.index in settings.autoOpenDoorIndexes }
        val targets = configuredTargets.ifEmpty {
            visibleDoors.takeIf { it.size == 1 }.orEmpty()
        }

        if (targets.isEmpty()) {
            addEvent(
                AccessEventType.ERROR,
                personName = null,
                cardNumber = null,
                message = "안면 인증 후 개방 대상 없음"
            )
            logSystem("안면 인증 성공, 개방 대상 없음")
            return false
        }

        addEvent(
            AccessEventType.FACE,
            personName = personName,
            cardNumber = null,
            message = "$personName 안면 인증"
        )
        return targets.all { door ->
            openDoor(door.index, auto = true, personName = personName)
        }
    }

    fun evaluateFaceEmbedding(embedding: FloatArray): FaceAuthDecision {
        if (!serialState.connected) {
            resetFaceCandidate()
            return FaceAuthDecision.Blocked("장비가 연결되지 않았습니다")
        }

        val candidates = faces.filter { it.embedding.size == FACE_EMBEDDING_SIZE }
        if (candidates.isEmpty()) {
            resetFaceCandidate()
            return FaceAuthDecision.Blocked("등록된 얼굴이 없습니다")
        }

        val best = candidates
            .map { face -> face to l2Distance(embedding, face.embedding) }
            .minByOrNull { it.second }
            ?: return FaceAuthDecision.Blocked("등록된 얼굴이 없습니다")

        val now = System.currentTimeMillis()
        val face = best.first
        val distance = best.second

        if (distance <= FACE_MATCH_L2_THRESHOLD) {
            val sameCandidate = face.name == lastFaceCandidateName &&
                now - lastFaceCandidateAt <= FACE_CONFIRMATION_WINDOW_MS
            lastFaceCandidateName = face.name
            lastFaceCandidateAt = now
            faceCandidateCount = if (sameCandidate) faceCandidateCount + 1 else 1
            if (faceCandidateCount < FACE_CONFIRMATION_COUNT) {
                return FaceAuthDecision.Pending("얼굴을 잠시 유지해 주세요")
            }
            if (now - lastFaceOpenAt >= FACE_OPEN_COOLDOWN_MS) {
                val opened = openDoorAfterFaceAuth(face.name)
                if (!opened) {
                    return FaceAuthDecision.OpenFailed(
                        personName = face.name,
                        message = "인증됐지만 문 개방 명령을 전송하지 못했습니다"
                    )
                }
                lastFaceOpenAt = now
            }
            return FaceAuthDecision.Granted(
                personName = face.name,
                distance = distance,
                message = "${face.name} 인증되었습니다"
            )
        }

        resetFaceCandidate()
        if (now - lastFaceFailureLogAt >= FACE_FAILURE_LOG_INTERVAL_MS) {
            lastFaceFailureLogAt = now
            addEvent(
                AccessEventType.DENIED,
                personName = null,
                cardNumber = null,
                message = "등록되지 않은 사용자입니다",
                detail = "얼굴 거리 ${"%.2f".format(Locale.US, distance)}"
            )
        }
        return FaceAuthDecision.Denied(
            distance = distance,
            message = "등록되지 않은 사용자입니다"
        )
    }

    fun recordFaceAuthFailure(message: String) {
        addEvent(
            AccessEventType.ERROR,
            personName = null,
            cardNumber = null,
            message = message
        )
        logSystem(message)
    }

    // -----------------------------------------------------------------------
    // 카드 인증 (onCardDetected)
    // -----------------------------------------------------------------------

    private fun onCardDetected(input: WiegandInput) {
        val key = cardKeyOf(input)
        val number = cardNumberOf(input)

        if (registrationActive) {
            capturedCard = CapturedCard(key, number)
            logSystem("등록 대기: 카드 감지 $number")
            return
        }

        when (val outcome = evaluateCard(key, number, cards, settings, doors)) {
            is CardOutcome.Tag -> addEvent(
                AccessEventType.TAG,
                personName = outcome.personName,
                cardNumber = number,
                message = outcome.personName?.let { "$it 태그" } ?: "카드 태그"
            )

            is CardOutcome.Granted -> {
                addEvent(
                    AccessEventType.GRANTED,
                    personName = outcome.personName,
                    cardNumber = number,
                    message = "${outcome.personName} 인증"
                )
                outcome.doorIndexes.forEach {
                    openDoor(
                        it,
                        auto = true,
                        personName = outcome.personName,
                        cardNumber = number
                    )
                }
            }

            is CardOutcome.NoTarget -> addEvent(
                AccessEventType.ERROR,
                personName = outcome.personName,
                cardNumber = number,
                message = "등록 카드 인증 후 개방 대상 없음"
            )

            is CardOutcome.Denied -> addEvent(
                AccessEventType.DENIED,
                personName = null,
                cardNumber = number,
                message = "미등록 카드"
            )
        }
    }

    // -----------------------------------------------------------------------
    // 카드 등록 대기 모드 (관리자)
    // -----------------------------------------------------------------------

    fun startCardRegistration() {
        if (!adminAuthenticated) return
        registrationActive = true
        capturedCard = null
        logSystem("카드 등록 대기 시작")
    }

    fun cancelCardRegistration() {
        if (registrationActive || capturedCard != null) {
            registrationActive = false
            capturedCard = null
        }
    }

    /** 감지된 카드에 이름을 붙여 저장. 성공 시 true. */
    fun confirmCardRegistration(name: String): Boolean {
        if (!adminAuthenticated) return false
        val captured = capturedCard ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val next = cards.filterNot { it.key == captured.key } +
            RegisteredCard(key = captured.key, name = trimmed, addedAt = System.currentTimeMillis())
        cards = next
        store.saveCards(next)
        registrationActive = false
        capturedCard = null
        logSystem("카드 등록: $trimmed (${captured.cardNumber})")
        return true
    }

    fun removeCard(key: String) {
        if (!adminAuthenticated) return
        val next = cards.filterNot { it.key == key }
        cards = next
        store.saveCards(next)
    }

    fun registerFace(name: String, embedding: FloatArray?): Boolean {
        if (!adminAuthenticated) return false
        val trimmed = name.trim()
        if (trimmed.isEmpty() || embedding == null || embedding.size != FACE_EMBEDDING_SIZE) return false
        val now = System.currentTimeMillis()
        val next = faces + RegisteredFace(
            id = "face-$now",
            name = trimmed,
            addedAt = now,
            embedding = embedding.toList()
        )
        faces = next
        store.saveFaces(next)
        logSystem("얼굴 등록: $trimmed")
        return true
    }

    fun removeFace(id: String) {
        if (!adminAuthenticated) return
        val next = faces.filterNot { it.id == id }
        faces = next
        store.saveFaces(next)
    }

    // -----------------------------------------------------------------------
    // 문 설정 / 자동개방 설정 (관리자)
    // -----------------------------------------------------------------------

    fun updateDoor(door: DoorConfig) {
        if (!adminAuthenticated) return
        val next = doors.map { if (it.index == door.index) door else it }
        doors = next
        store.saveDoors(next)
    }

    fun updateSettings(newSettings: ServiceSettings) {
        if (!adminAuthenticated) return
        settings = newSettings
        store.saveSettings(newSettings)
    }

    fun resetAllData() {
        if (!adminAuthenticated) return
        store.clearAll()
        doors = ServiceStore.defaultDoors()
        cards = emptyList()
        faces = emptyList()
        settings = ServiceSettings()
        accessEvents.clear()
        store.saveAccessEvents(emptyList())
        cancelCardRegistration()
        logSystem("데이터 초기화")
    }

    // -----------------------------------------------------------------------
    // 장비 진단 액션 (관리자)
    // -----------------------------------------------------------------------

    fun queryWiegand() {
        if (!adminAuthenticated) return
        val packet = AccessLinkProtocol.getWiegandInputData()
        if (sendPacket(packet, "Wiegand 조회")) {
            raw32Buffer.clear()
        }
    }

    fun sendWiegandOutput(useParity: Boolean, input: String): String {
        if (!adminAuthenticated) return "관리자 인증 필요"
        val parseResult = WiegandPayloadCodec.parseOutputHex(input)
        if (parseResult is WiegandPayloadParseResult.Error) {
            val message = "Wiegand OUT 실패: ${parseResult.message}"
            logSystem(message)
            return message
        }
        val payload = (parseResult as WiegandPayloadParseResult.Success).bytes
        val useParityValue = if (useParity) 0 else 1
        val packet = AccessLinkProtocol.wiegandOut(useParityValue, payload)
        return if (sendPacket(packet, "Wiegand OUT ${payload.size} bytes")) {
            wiegandTxLogs.add(
                0,
                WiegandOutputLog(
                    parity = if (useParity) "Parity 출력" else "Parity 미출력",
                    dataHex = payload.toHexString(),
                    packetHex = packet.toHexString()
                )
            )
            trim(wiegandTxLogs, MAX_WIEGAND_TX_LOGS)
            "Wiegand OUT TX ${payload.size} bytes"
        } else {
            "Wiegand OUT 실패"
        }
    }

    fun sendSerial(port: SerialPort, mode: SerialInputMode, input: String): String {
        if (!adminAuthenticated) return "관리자 인증 필요"
        val parseResult = SerialPayloadCodec.parse(input, mode)
        if (parseResult is SerialPayloadParseResult.Error) {
            val message = "${port.label} TX 실패: ${parseResult.message}"
            logSystem(message)
            return message
        }
        val payload = (parseResult as SerialPayloadParseResult.Success).bytes
        val useSerialPort = when (port) {
            SerialPort.RS232 -> 0
            SerialPort.RS485 -> 1
        }
        val packet = AccessLinkProtocol.serialSend(useSerialPort, payload)
        return if (sendPacket(packet, "${port.label} TX ${payload.size} bytes")) {
            addSerialPortLog(
                port,
                SerialPortLog(
                    direction = "TX",
                    hex = payload.toHexString(),
                    ascii = SerialPayloadCodec.safeAscii(payload),
                    packetHex = packet.toHexString()
                )
            )
            "${port.label} TX ${payload.size} bytes"
        } else {
            "${port.label} TX 실패"
        }
    }

    fun clearSerial(port: SerialPort) {
        if (!adminAuthenticated) return
        when (port) {
            SerialPort.RS232 -> rs232Logs.clear()
            SerialPort.RS485 -> rs485Logs.clear()
        }
        logSystem("${port.label} 로그 초기화")
    }

    fun clearWiegandDiagnostics() {
        if (!adminAuthenticated) return
        raw32Buffer.clear()
        lastRaw32CandidateHex = null
        lastRaw32CandidateAt = 0L
        lastRaw32CandidateCount = 0
        wiegandTxLogs.clear()
        diagnostics = diagnostics.copy(
            lastWiegand = null,
            rawWiegandStatus = null,
            lastCardHex = null,
            lastWiegandRawHex = null,
            lastWiegandReceivedAt = null,
            wiegandReceiveCount = 0
        )
        logSystem("Wiegand 초기화")
    }

    // -----------------------------------------------------------------------
    // 연결/장치 상태 반영 (MainActivity → 컨트롤러)
    // -----------------------------------------------------------------------

    fun onConnectionStatus(status: AccessLinkConnectionStatus) {
        if (!status.connected) {
            raw32Buffer.clear()
            lastRaw32CandidateHex = null
            lastRaw32CandidateAt = 0L
            lastRaw32CandidateCount = 0
            resetFaceCandidate()
        }
        serialState = SerialUiState(
            connected = status.connected,
            baudRate = status.baudRate,
            status = when (status.state) {
                AccessLinkConnectionState.DISCONNECTED -> status.message
                AccessLinkConnectionState.CONNECTING -> "연결 중"
                AccessLinkConnectionState.CONNECTED -> "연결됨: ${status.baudRate}bps"
                AccessLinkConnectionState.ERROR -> status.message
            }
        )
    }

    fun setUsbDevices(devices: List<UsbDeviceSnapshot>) {
        usbDevices.clear()
        usbDevices.addAll(devices)
    }

    fun setEthernet(state: EthernetUiState) {
        ethernetState = state
    }

    fun logSystem(message: String) {
        systemLogs.add(0, "[${nowHms()}] $message")
        trim(systemLogs, MAX_SYSTEM_LOGS)
    }

    fun handleConnectionEvent(event: AccessLinkConnectionEvent) {
        when (event) {
            is AccessLinkConnectionEvent.RawDataReceived -> {
                if (!event.data.hasProtocolStart()) {
                    onRawSerial(event.data)
                }
            }

            is AccessLinkConnectionEvent.PacketReceived -> {
                raw32Buffer.clear()
                onPacket(event.packet.raw)
                logSystem("수신 ${event.packet.raw.toHexString()} / ${AccessLinkProtocol.describeIncoming(event.packet.raw)}")
            }

            is AccessLinkConnectionEvent.ProtocolErrorReceived -> {
                logSystem("프로토콜 오류: ${event.error.detail}")
            }

            is AccessLinkConnectionEvent.PacketSent -> {
                // 송신 로그는 각 기능(openDoor/queryWiegand 등)에서 기록한다.
            }
        }
    }

    // -----------------------------------------------------------------------
    // 수신 처리 (raw32 + 프로토콜 패킷)
    // -----------------------------------------------------------------------

    private fun onPacket(data: ByteArray) {
        if (!data.isAccessLinkPacket()) {
            diagnostics = diagnostics.copy(lastRawHex = data.toHexString())
            return
        }
        val command = data[2].toInt() and 0xFF
        val payload = data.copyOfRange(3, data.size - 1)
        val rawHex = data.toHexString()
        when (command) {
            AccessLinkProtocol.CMD_GET_WIEGAND_INPUT_DATA -> {
                val decoded = AccessLinkProtocol.decodeWiegandInput(payload)
                diagnostics = diagnostics.copy(
                    lastWiegand = decoded.summary,
                    rawWiegandStatus = null,
                    lastCardHex = decoded.dataHex,
                    lastWiegandRawHex = rawHex,
                    lastWiegandReceivedAt = nowHms(),
                    wiegandReceiveCount = diagnostics.wiegandReceiveCount + 1,
                    lastRawHex = rawHex
                )
                onCardDetected(decoded)
            }

            AccessLinkProtocol.CMD_GET_RECV_DATA_RS232 -> {
                addSerialPortLog(
                    SerialPort.RS232,
                    SerialPortLog("RX", payload.toHexString(), SerialPayloadCodec.safeAscii(payload), rawHex)
                )
                diagnostics = diagnostics.copy(lastRs232 = payload.toHexString(), lastRawHex = rawHex)
            }

            AccessLinkProtocol.CMD_GET_RECV_DATA_RS485 -> {
                addSerialPortLog(
                    SerialPort.RS485,
                    SerialPortLog("RX", payload.toHexString(), SerialPayloadCodec.safeAscii(payload), rawHex)
                )
                diagnostics = diagnostics.copy(lastRs485 = payload.toHexString(), lastRawHex = rawHex)
            }

            AccessLinkProtocol.CMD_GET_INPUT_PORT_0 ->
                diagnostics = diagnostics.copy(input0 = payload.toInputStatus(), lastRawHex = rawHex)

            AccessLinkProtocol.CMD_GET_INPUT_PORT_1 ->
                diagnostics = diagnostics.copy(input1 = payload.toInputStatus(), lastRawHex = rawHex)

            AccessLinkProtocol.CMD_SET_RELAY_CONTROL ->
                diagnostics = diagnostics.copy(lastRelayHex = rawHex, lastRawHex = rawHex)

            else -> diagnostics = diagnostics.copy(lastRawHex = rawHex)
        }
    }

    private fun onRawSerial(data: ByteArray) {
        val rawHex = data.toHexString()
        val raw32 = updateRaw32Candidate(data)
        if (raw32 != null) {
            val stable = markRaw32Candidate(raw32.dataHex)
            if (stable) {
                diagnostics = diagnostics.copy(
                    lastWiegand = raw32.summary,
                    rawWiegandStatus = null,
                    lastCardHex = raw32.dataHex,
                    lastWiegandRawHex = rawHex,
                    lastWiegandReceivedAt = nowHms(),
                    wiegandReceiveCount = diagnostics.wiegandReceiveCount + 1,
                    lastRawHex = rawHex
                )
                logSystem("수신 $rawHex / 카드 확인: ${raw32.summary}")
                onCardDetected(raw32)
            } else {
                diagnostics = diagnostics.copy(
                    rawWiegandStatus = "반복 확인 ${raw32.summary}",
                    lastRawHex = rawHex
                )
                logSystem("수신 $rawHex / 반복 확인: ${raw32.summary}")
            }
            return
        }

        diagnostics = diagnostics.copy(
            rawWiegandStatus = if (data.size <= RAW_32_CARD_BYTES) "조각 수신 ${raw32Buffer.size}/4 bytes" else "원시 수신",
            lastRawHex = rawHex
        )
        logSystem(
            if (data.size <= RAW_32_CARD_BYTES) "수신 $rawHex / 조각 수신: ${raw32Buffer.size}/4 bytes"
            else "수신 $rawHex / 원시 수신"
        )
    }

    // raw32 조립: 300ms 조각조립 후 4바이트 확정
    private fun updateRaw32Candidate(data: ByteArray): WiegandInput? {
        val now = System.currentTimeMillis()
        if (now - raw32BufferUpdatedAt > RAW_32_BUFFER_TIMEOUT_MS) {
            raw32Buffer.clear()
        }
        raw32BufferUpdatedAt = now

        if (data.size > RAW_32_CARD_BYTES) {
            raw32Buffer.clear()
            return null
        }

        data.forEach { byte -> raw32Buffer.addLast(byte) }
        while (raw32Buffer.size > RAW_32_CARD_BYTES) {
            raw32Buffer.removeFirst()
        }

        if (raw32Buffer.size != RAW_32_CARD_BYTES) return null
        val candidate = AccessLinkProtocol.decodeRaw32Input(raw32Buffer.toByteArray())
        raw32Buffer.clear()
        return candidate
    }

    // raw32 안정화: 2초 내 동일 후보 2회 확정
    private fun markRaw32Candidate(dataHex: String): Boolean {
        val now = System.currentTimeMillis()
        val sameCandidate = dataHex == lastRaw32CandidateHex && now - lastRaw32CandidateAt <= RAW_32_STABLE_WINDOW_MS
        lastRaw32CandidateHex = dataHex
        lastRaw32CandidateAt = now
        lastRaw32CandidateCount = if (sameCandidate) lastRaw32CandidateCount + 1 else 1
        return lastRaw32CandidateCount >= 2
    }

    // -----------------------------------------------------------------------
    // 내부 유틸
    // -----------------------------------------------------------------------

    private fun sendPacket(packet: ByteArray, label: String): Boolean {
        return try {
            send(packet)
            logSystem("송신 $label ${packet.toHexString()}")
            true
        } catch (exception: Exception) {
            logSystem("송신 실패 $label: ${exception.message ?: "알 수 없는 오류"}")
            false
        }
    }

    private fun addEvent(
        type: AccessEventType,
        personName: String?,
        cardNumber: String?,
        message: String,
        detail: String? = null
    ) {
        accessEvents.add(
            0,
            AccessEvent(
                time = nowHms(),
                type = type,
                personName = personName,
                cardNumber = cardNumber,
                message = message,
                detail = detail
            )
        )
        trim(accessEvents, MAX_EVENTS)
        store.saveAccessEvents(accessEvents.toList())
    }

    private fun addSerialPortLog(port: SerialPort, entry: SerialPortLog) {
        val target = when (port) {
            SerialPort.RS232 -> rs232Logs
            SerialPort.RS485 -> rs485Logs
        }
        target.add(0, entry)
        trim(target, MAX_SERIAL_LOGS)
    }

    private fun <T> trim(list: MutableList<T>, max: Int) {
        while (list.size > max) {
            list.removeAt(list.size - 1)
        }
    }

    private fun l2Distance(left: FloatArray, right: List<Float>): Float {
        return sqrt(left.mapIndexed { index, value ->
            val diff = value - right[index]
            diff * diff
        }.sum())
    }

    private fun resetFaceCandidate() {
        lastFaceCandidateName = null
        lastFaceCandidateAt = 0L
        faceCandidateCount = 0
    }
}

sealed interface FaceAuthDecision {
    val message: String

    data class Granted(
        val personName: String,
        val distance: Float,
        override val message: String
    ) : FaceAuthDecision

    data class Denied(
        val distance: Float,
        override val message: String
    ) : FaceAuthDecision

    data class Blocked(
        override val message: String
    ) : FaceAuthDecision

    data class Pending(
        override val message: String
    ) : FaceAuthDecision

    data class OpenFailed(
        val personName: String,
        override val message: String
    ) : FaceAuthDecision
}

data class DiagnosticsState(
    val lastWiegand: String? = null,
    val rawWiegandStatus: String? = null,
    val lastCardHex: String? = null,
    val lastWiegandRawHex: String? = null,
    val lastWiegandReceivedAt: String? = null,
    val wiegandReceiveCount: Int = 0,
    val lastRs232: String? = null,
    val lastRs485: String? = null,
    val input0: String? = null,
    val input1: String? = null,
    val lastRelayHex: String? = null,
    val lastRawHex: String? = null
)

data class SerialPortLog(
    val direction: String,
    val hex: String,
    val ascii: String,
    val packetHex: String
)

data class WiegandOutputLog(
    val parity: String,
    val dataHex: String,
    val packetHex: String
)

data class RelayControlLog(
    val relay: String,
    val command: String,
    val packetHex: String
)

val SerialPort.label: String
    get() = when (this) {
        SerialPort.RS232 -> "RS-232"
        SerialPort.RS485 -> "RS-485"
    }

private fun nowHms(): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())

private fun ByteArray.isAccessLinkPacket(): Boolean =
    size >= 4 && first() == 0x02.toByte() && last() == 0x03.toByte()

private fun ByteArray.hasProtocolStart(): Boolean = any { it == 0x02.toByte() }

private fun ByteArray.toInputStatus(): String {
    val value = firstOrNull()?.toInt()?.and(0xFF) ?: return "미확인"
    return when (value) {
        0, '0'.code -> "Released"
        1, '1'.code -> "Pressed"
        else -> "Unknown ${toHexString()}"
    }
}
