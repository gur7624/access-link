package com.shinhwa.accesslinktester

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shinhwa.accesslinktester.ui.theme.AccessLinkTesterTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ACTION_USB_PERMISSION = "com.shinhwa.accesslinktester.USB_PERMISSION"
private const val ACCESS_LINK_SERIAL_VENDOR_ID = 0x1A86
private const val ACCESS_LINK_SERIAL_PRODUCT_ID = 0x7523
private const val ACCESS_LINK_LAN_VENDOR_ID = 0x0BDA
private const val ACCESS_LINK_LAN_PRODUCT_ID = 0x8152
private const val RAW_32_CARD_BYTES = 4
private const val RAW_32_BUFFER_TIMEOUT_MS = 300L
private const val RAW_32_STABLE_WINDOW_MS = 2_000L

class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbManager
    private lateinit var connectivityManager: ConnectivityManager
    private val usbDevices = mutableStateListOf<UsbDeviceSnapshot>()
    private val logs = mutableStateListOf<String>()
    private val serialState = mutableStateOf(SerialUiState())
    private val ethernetState = mutableStateOf(EthernetUiState())
    private val portState = mutableStateOf(PortDashboardState())
    private val adminMode = mutableStateOf(false)
    private var serialController: AccessLinkSerialController? = null
    private val raw32Buffer = ArrayDeque<Byte>()
    private var raw32BufferUpdatedAt = 0L
    private var lastRaw32CandidateHex: String? = null
    private var lastRaw32CandidateAt = 0L
    private var lastRaw32CandidateCount = 0

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.usbDevice()
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    appendLog(
                        if (granted) {
                            "USB 권한 승인: ${device?.deviceName ?: "알 수 없는 장치"}"
                        } else {
                            "USB 권한 거부: ${device?.deviceName ?: "알 수 없는 장치"}"
                        }
                    )
                    refreshUsbDevices()
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    appendLog("USB 장치 연결 감지")
                    refreshUsbDevices()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    appendLog("USB 장치 분리 감지")
                    refreshUsbDevices()
                }
            }
        }
    }

    private val ethernetCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { refreshEthernetState() }
        }

        override fun onLost(network: Network) {
            runOnUiThread { refreshEthernetState() }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            runOnUiThread { refreshEthernetState() }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            runOnUiThread { refreshEthernetState() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(UsbManager::class.java)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        registerUsbReceiver()
        registerEthernetCallback()
        refreshUsbDevices()
        refreshEthernetState()
        enableEdgeToEdge()

        setContent {
            AccessLinkTesterTheme(dynamicColor = false) {
                UsbDiagnosticApp(
                    devices = usbDevices,
                    serialState = serialState.value,
                    ethernetState = ethernetState.value,
                    portState = portState.value,
                    logs = logs,
                    adminMode = adminMode.value,
                    onRefresh = {
                        appendLog("USB 장치 목록 새로고침")
                        refreshUsbDevices()
                        refreshEthernetState()
                    },
                    onAdminToggle = { adminMode.value = !adminMode.value },
                    onRequestPermission = ::requestUsbPermission,
                    onConnectSerial = ::connectSerial,
                    onDisconnectSerial = ::disconnectSerial,
                    onWiegandQuery = ::sendWiegandQuery,
                    onRelayCommand = ::sendRelayCommand
                )
            }
        }
    }

    override fun onDestroy() {
        disconnectSerial()
        unregisterEthernetCallback()
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun refreshUsbDevices() {
        val snapshots = usbManager.deviceList.values
            .sortedWith(compareBy<UsbDevice> { it.vendorId }.thenBy { it.productId }.thenBy { it.deviceName })
            .map { device -> device.toSnapshot(usbManager.hasPermission(device)) }

        usbDevices.clear()
        usbDevices.addAll(snapshots)

        if (snapshots.isEmpty()) {
            appendLog("감지된 USB 장치 없음")
        } else {
            appendLog("감지된 USB 장치 ${snapshots.size}개")
        }
    }

    private fun registerEthernetCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, ethernetCallback)
    }

    private fun unregisterEthernetCallback() {
        runCatching { connectivityManager.unregisterNetworkCallback(ethernetCallback) }
    }

    @Suppress("DEPRECATION")
    private fun refreshEthernetState() {
        val networks = connectivityManager.allNetworks.mapNotNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) != true) {
                return@mapNotNull null
            }
            val linkProperties = connectivityManager.getLinkProperties(network)
            val interfaceName = linkProperties?.interfaceName
            val addresses = linkProperties?.linkAddresses
                ?.map { it.address.hostAddress.orEmpty() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            EthernetUiState(
                connected = true,
                interfaceName = interfaceName,
                detail = listOfNotNull(
                    interfaceName,
                    addresses.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ).joinToString(" / ").ifBlank { "링크 감지" }
            )
        }

        ethernetState.value = networks.firstOrNull() ?: EthernetUiState(
            connected = false,
            interfaceName = null,
            detail = "랜선 링크 미감지"
        )
    }

    private fun requestUsbPermission(device: UsbDeviceSnapshot) {
        val usbDevice = usbManager.deviceList.values.firstOrNull { it.deviceName == device.deviceName }
        if (usbDevice == null) {
            appendLog("권한 요청 실패: 장치를 다시 찾을 수 없음")
            refreshUsbDevices()
            return
        }

        if (usbManager.hasPermission(usbDevice)) {
            appendLog("이미 권한 있음: ${usbDevice.deviceName}")
            refreshUsbDevices()
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(
            this,
            usbDevice.deviceName.hashCode(),
            Intent(ACTION_USB_PERMISSION).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(usbDevice, permissionIntent)
        appendLog("USB 권한 요청: ${usbDevice.deviceName}")
    }

    private fun connectSerial(baudRate: Int) {
        val device = usbManager.deviceList.values.firstOrNull { it.isAccessLinkSerialDevice() }
        if (device == null) {
            appendLog("CH340 Serial 장치를 찾을 수 없습니다.")
            serialState.value = serialState.value.copy(status = "CH340 장치 없음")
            return
        }

        if (!usbManager.hasPermission(device)) {
            appendLog("Serial 연결 전 USB 권한이 필요합니다.")
            requestUsbPermission(device.toSnapshot(false))
            serialState.value = serialState.value.copy(status = "USB 권한 필요")
            return
        }

        disconnectSerial()
        try {
            val controller = AccessLinkSerialController(
                usbManager = usbManager,
                device = device,
                baudRate = baudRate,
                onLog = { message -> runOnUiThread { appendLog(message) } },
                onReceive = { data ->
                    runOnUiThread {
                        appendLog(handleSerialReceive(data))
                    }
                }
            )
            controller.open()
            serialController = controller
            serialState.value = SerialUiState(
                connected = true,
                baudRate = baudRate,
                status = "연결됨: ${baudRate}bps"
            )
        } catch (exception: Exception) {
            serialController = null
            serialState.value = SerialUiState(
                connected = false,
                baudRate = baudRate,
                status = "연결 실패: ${exception.message ?: "알 수 없는 오류"}"
            )
            appendLog(serialState.value.status)
        }
    }

    private fun disconnectSerial() {
        serialController?.close()
        serialController = null
        if (serialState.value.connected) {
            appendLog("CH340 Serial 연결 해제")
        }
        serialState.value = serialState.value.copy(connected = false, status = "연결 안 됨")
    }

    private fun sendRelayCommand(useRelay: Int, outputType: Int, time: Int) {
        val packet = AccessLinkProtocol.relayControl(useRelay, outputType, time)
        try {
            val controller = serialController ?: error("Serial 연결이 필요합니다.")
            controller.write(packet)
            val relayState = if (outputType == 1) "ON 송신" else "OFF 송신"
            portState.value = portState.value.copy(
                relay0 = if (useRelay == 0 || useRelay == 1) relayState else portState.value.relay0,
                relay1 = if (useRelay == 0 || useRelay == 2) relayState else portState.value.relay1,
                lastRelayHex = packet.toHexString()
            )
            appendLog("송신 ${packet.toHexString()}")
        } catch (exception: Exception) {
            appendLog("릴레이 명령 실패: ${exception.message ?: "알 수 없는 오류"}")
        }
    }

    private fun sendWiegandQuery() {
        val packet = AccessLinkProtocol.getWiegandInputData()
        try {
            val controller = serialController ?: error("Serial 연결이 필요합니다.")
            controller.write(packet)
            raw32Buffer.clear()
            appendLog("Wiegand 입력 조회 송신 ${packet.toHexString()}")
        } catch (exception: Exception) {
            appendLog("Wiegand 입력 조회 실패: ${exception.message ?: "알 수 없는 오류"}")
        }
    }

    private fun handleSerialReceive(data: ByteArray): String {
        val rawHex = data.toHexString()
        if (data.isAccessLinkPacket()) {
            raw32Buffer.clear()
            updatePortState(data)
            return "수신 $rawHex / ${AccessLinkProtocol.describeIncoming(data)}"
        }

        val raw32 = updateRaw32Candidate(data)
        if (raw32 != null) {
            val stableRaw32 = markRaw32Candidate(raw32.dataHex)
            portState.value = if (stableRaw32) {
                portState.value.copy(
                    lastWiegand = raw32.summary,
                    rawWiegandStatus = null,
                    lastCardHex = raw32.dataHex,
                    lastRawHex = rawHex
                )
            } else {
                portState.value.copy(
                    rawWiegandStatus = "검증 전 ${raw32.summary}",
                    lastRawHex = rawHex
                )
            }
            return if (stableRaw32) {
                "수신 $rawHex / 32bit 카드 확인: ${raw32.summary}"
            } else {
                "수신 $rawHex / 32bit 후보 반복 확인 중: ${raw32.summary}"
            }
        }

        portState.value = portState.value.copy(
            rawWiegandStatus = if (data.size <= RAW_32_CARD_BYTES) {
                "조각 수신 ${raw32Buffer.size}/4 bytes"
            } else {
                "원시 수신"
            },
            lastRawHex = rawHex
        )
        return if (data.size <= RAW_32_CARD_BYTES) {
            "수신 $rawHex / 32bit 조각 수신: ${raw32Buffer.size}/4 bytes"
        } else {
            "수신 $rawHex / 원시 수신: $rawHex"
        }
    }

    private fun markRaw32Candidate(dataHex: String): Boolean {
        val now = System.currentTimeMillis()
        val sameCandidate = dataHex == lastRaw32CandidateHex && now - lastRaw32CandidateAt <= RAW_32_STABLE_WINDOW_MS
        lastRaw32CandidateHex = dataHex
        lastRaw32CandidateAt = now
        lastRaw32CandidateCount = if (sameCandidate) lastRaw32CandidateCount + 1 else 1
        return lastRaw32CandidateCount >= 2
    }

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

    private fun updatePortState(data: ByteArray) {
        if (!data.isAccessLinkPacket()) {
            portState.value = portState.value.copy(lastRawHex = data.toHexString())
            return
        }

        val command = data[2].toInt() and 0xFF
        val payload = data.copyOfRange(3, data.size - 1)
        val rawHex = data.toHexString()
        portState.value = when (command) {
            AccessLinkProtocol.CMD_GET_WIEGAND_INPUT_DATA -> {
                val decoded = AccessLinkProtocol.decodeWiegandInput(payload)
                portState.value.copy(
                    lastWiegand = decoded.summary,
                    rawWiegandStatus = null,
                    lastCardHex = decoded.dataHex,
                    lastRawHex = rawHex
                )
            }

            AccessLinkProtocol.CMD_GET_RECV_DATA_RS232 -> {
                portState.value.copy(lastRs232 = payload.toHexString(), lastRawHex = rawHex)
            }

            AccessLinkProtocol.CMD_GET_RECV_DATA_RS485 -> {
                portState.value.copy(lastRs485 = payload.toHexString(), lastRawHex = rawHex)
            }

            AccessLinkProtocol.CMD_GET_INPUT_PORT_0 -> {
                portState.value.copy(input0 = payload.toInputStatus(), lastRawHex = rawHex)
            }

            AccessLinkProtocol.CMD_GET_INPUT_PORT_1 -> {
                portState.value.copy(input1 = payload.toInputStatus(), lastRawHex = rawHex)
            }

            AccessLinkProtocol.CMD_SET_RELAY_CONTROL -> {
                portState.value.copy(lastRelayHex = rawHex, lastRawHex = rawHex)
            }

            else -> portState.value.copy(lastRawHex = rawHex)
        }
    }

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        logs.add(0, "[$time] $message")
        if (logs.size > 80) {
            logs.removeRange(80, logs.size)
        }
    }
}

private fun ByteArray.isAccessLinkPacket(): Boolean {
    return size >= 4 && first() == 0x02.toByte() && last() == 0x03.toByte()
}

@Composable
private fun UsbDiagnosticApp(
    devices: List<UsbDeviceSnapshot>,
    serialState: SerialUiState,
    ethernetState: EthernetUiState,
    portState: PortDashboardState,
    logs: List<String>,
    adminMode: Boolean,
    onRefresh: () -> Unit,
    onAdminToggle: () -> Unit,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
    onWiegandQuery: () -> Unit,
    onRelayCommand: (Int, Int, Int) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeaderCard(
                    connectedCount = devices.size,
                    permissionCount = devices.count { it.hasPermission },
                    adminMode = adminMode,
                    onAdminToggle = onAdminToggle,
                    onRefresh = onRefresh
                )
            }

            if (adminMode) {
                item {
                    AdminDiagnosticsScreen(
                        devices = devices,
                        serialState = serialState,
                        ethernetState = ethernetState,
                        portState = portState,
                        logs = logs,
                        onRequestPermission = onRequestPermission
                    )
                }
            } else {
                item {
                    FieldPortDashboard(
                        serialDevice = devices.firstOrNull { it.isAccessLinkSerial },
                        lanDevice = devices.firstOrNull { it.isAccessLinkLan },
                        serialState = serialState,
                        ethernetState = ethernetState,
                        portState = portState
                    )
                }

                item {
                    SerialControlCard(
                        serialDevice = devices.firstOrNull { it.isAccessLinkSerial },
                        serialState = serialState,
                        onConnectSerial = onConnectSerial,
                        onDisconnectSerial = onDisconnectSerial,
                        onRequestPermission = onRequestPermission,
                        onWiegandQuery = onWiegandQuery,
                        onRelayCommand = onRelayCommand
                    )
                }

                item {
                    LogCard(logs = logs)
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    connectedCount: Int,
    permissionCount: Int,
    adminMode: Boolean,
    onAdminToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102033))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "ACCESS LINK",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "USB 진단",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC7D2E1)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAdminToggle) {
                        Text(if (adminMode) "메인" else "관리자")
                    }
                    Button(onClick = onRefresh) {
                        Text("새로고침")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("USB $connectedCount", if (connectedCount > 0) PassGreen else WaitGray)
                StatusChip("권한 $permissionCount", if (permissionCount > 0) ActiveBlue else WaitGray)
                StatusChip(
                    text = if (connectedCount > 0) "READY" else "WAIT",
                    color = if (connectedCount > 0) ActiveBlue else WaitGray
                )
            }
        }
    }
}

@Composable
private fun StatusSummary(devices: List<UsbDeviceSnapshot>) {
    val title = when {
        devices.isEmpty() -> "USB 장치가 아직 감지되지 않았습니다"
        devices.any { it.hasPermission } -> "진단 가능"
        else -> "권한 요청 필요"
    }
    val color = when {
        devices.isEmpty() -> WaitGray
        devices.any { it.hasPermission } -> PassGreen
        else -> ActiveBlue
    }

    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("상태", style = MaterialTheme.typography.labelLarge, color = Color(0xFF5B6472))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            StatusChip(text = if (devices.any { it.hasPermission }) "PASS" else "CHECK", color = color)
        }
    }
}

@Composable
private fun AdminDiagnosticsScreen(
    devices: List<UsbDeviceSnapshot>,
    serialState: SerialUiState,
    ethernetState: EthernetUiState,
    portState: PortDashboardState,
    logs: List<String>,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit
) {
    val serialDevice = devices.firstOrNull { it.isAccessLinkSerial }
    val lanDevice = devices.firstOrNull { it.isAccessLinkLan }
    val findings = buildDiagnosticFindings(
        serialDevice = serialDevice,
        lanDevice = lanDevice,
        serialState = serialState,
        ethernetState = ethernetState,
        portState = portState
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("관리자 진단", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                findings.forEach { finding ->
                    DiagnosticFindingRow(finding)
                }
            }
        }

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("원본 데이터", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DetailGrid(
                    items = listOf(
                        "Serial baudrate" to serialState.baudRate.toString(),
                        "Serial 상태" to serialState.status,
                        "Ethernet 링크" to ethernetState.detail,
                        "카드 HEX" to (portState.lastCardHex ?: "-"),
                        "원본 HEX" to (portState.lastRawHex ?: "-"),
                        "Relay HEX" to (portState.lastRelayHex ?: "-")
                    )
                )
            }
        }

        if (devices.isEmpty()) {
            EmptyDeviceCard()
        } else {
            devices.forEach { device ->
                DeviceCard(
                    device = device,
                    onRequestPermission = { onRequestPermission(device) }
                )
            }
        }

        LogCard(logs = logs, title = "전체 로그", limit = 80)
    }
}

@Composable
private fun DiagnosticFindingRow(finding: DiagnosticFinding) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(finding.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                StatusChip(finding.level, finding.color)
            }
            Text("위치: ${finding.area}", style = MaterialTheme.typography.bodySmall)
            Text("근거: ${finding.evidence}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
            Text("다음: ${finding.nextStep}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
        }
    }
}

@Composable
private fun EmptyDeviceCard() {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("연결 안내", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("ACCESS LINK의 USB-C 포트를 Android 휴대폰에 연결한 뒤 새로고침을 누르세요.")
            Text("장치가 표시되면 권한 요청 버튼으로 Vendor ID, Product ID, Interface 정보를 확인할 수 있습니다.")
        }
    }
}

@Composable
private fun FieldPortDashboard(
    serialDevice: UsbDeviceSnapshot?,
    lanDevice: UsbDeviceSnapshot?,
    serialState: SerialUiState,
    ethernetState: EthernetUiState,
    portState: PortDashboardState
) {
    val cardConfirmed = portState.lastWiegand != null
    val digitalConfirmed = portState.input0 != null || portState.input1 != null
    val rsConfirmed = portState.lastRs232 != null || portState.lastRs485 != null

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("상태", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            PortStatusRow(
                title = "USB Serial",
                description = "",
                value = serialDevice?.let { "${it.vendorIdHex}:${it.productIdHex} / ${serialState.status}" } ?: "CH340 미감지",
                status = when {
                    serialDevice == null -> "미감지"
                    !serialDevice.hasPermission -> "권한 필요"
                    serialState.connected -> "연결"
                    else -> "대기"
                },
                color = when {
                    serialState.connected -> PassGreen
                    serialDevice == null -> FailRed
                    else -> ActiveBlue
                }
            )

            PortStatusRow(
                title = "USB LAN 장치",
                description = "",
                value = lanDevice?.let { "${it.vendorIdHex}:${it.productIdHex}" } ?: "장치 미감지",
                status = if (lanDevice != null) "장치 감지" else "미감지",
                color = if (lanDevice != null) ActiveBlue else WaitGray
            )

            PortStatusRow(
                title = "Ethernet 링크",
                description = "",
                value = ethernetState.detail,
                status = if (ethernetState.connected) "링크 감지" else "미감지",
                color = if (ethernetState.connected) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Wiegand",
                description = "",
                value = portState.rawWiegandStatus ?: portState.lastCardHex?.let { "카드 HEX $it" } ?: "미수신",
                status = when {
                    cardConfirmed -> "통신 확인"
                    portState.rawWiegandStatus != null -> "검증 전"
                    else -> "미확인"
                },
                color = when {
                    cardConfirmed -> PassGreen
                    portState.rawWiegandStatus != null -> ActiveBlue
                    else -> WaitGray
                }
            )

            PortStatusRow(
                title = "카드 데이터",
                description = "",
                value = portState.lastWiegand ?: "미확정",
                status = if (cardConfirmed) "확정" else "미확정",
                color = if (cardConfirmed) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Digital Input",
                description = "",
                value = "IN0 ${portState.input0 ?: "미확인"} / IN1 ${portState.input1 ?: "미확인"}",
                status = if (digitalConfirmed) "통신 확인" else "미확인",
                color = if (digitalConfirmed) ActiveBlue else WaitGray
            )

            PortStatusRow(
                title = "RS-232 / RS-485",
                description = "",
                value = "232 ${portState.lastRs232 ?: "-"} / 485 ${portState.lastRs485 ?: "-"}",
                status = if (rsConfirmed) "통신 확인" else "미확인",
                color = if (rsConfirmed) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Relay",
                description = "",
                value = "R0 ${portState.relay0 ?: "-"} / R1 ${portState.relay1 ?: "-"}",
                status = if (serialState.connected) "테스트 가능" else "Serial 필요",
                color = if (serialState.connected) ActiveBlue else WaitGray
            )

        }
    }
}

@Composable
private fun PortStatusRow(
    title: String,
    description: String,
    value: String,
    status: String,
    color: Color
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
                }
                Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            StatusChip(status, color)
        }
    }
}

@Composable
private fun SerialControlCard(
    serialDevice: UsbDeviceSnapshot?,
    serialState: SerialUiState,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit,
    onWiegandQuery: () -> Unit,
    onRelayCommand: (Int, Int, Int) -> Unit
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("제어", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(serialState.status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
                }
                StatusChip(
                    text = if (serialState.connected) "Serial 연결" else "대기",
                    color = if (serialState.connected) PassGreen else WaitGray
                )
            }

            if (serialDevice == null) {
                Text("CH340 미감지")
                return@InfoCard
            }

            if (!serialDevice.hasPermission) {
                Button(onClick = { onRequestPermission(serialDevice) }, modifier = Modifier.fillMaxWidth()) {
                    Text("CH340 USB 권한 요청")
                }
                return@InfoCard
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onConnectSerial(9600) }, modifier = Modifier.weight(1f)) {
                    Text("9600")
                }
                Button(onClick = { onConnectSerial(115200) }, modifier = Modifier.weight(1f)) {
                    Text("115200")
                }
            }

            Text(
                "Serial 속도",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5B6472)
            )

            if (serialState.connected) {
                Button(onClick = onDisconnectSerial, modifier = Modifier.fillMaxWidth()) {
                    Text("Serial 연결 해제")
                }

                Button(onClick = onWiegandQuery, modifier = Modifier.fillMaxWidth()) {
                    Text("Wiegand 입력 조회")
                }

                Text("Relay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                RelayButtonRow(
                    title = "Relay 0",
                    onOn = { onRelayCommand(1, 1, 0) },
                    onOff = { onRelayCommand(1, 0, 0) }
                )
                RelayButtonRow(
                    title = "Relay 1",
                    onOn = { onRelayCommand(2, 1, 0) },
                    onOff = { onRelayCommand(2, 0, 0) }
                )
                RelayButtonRow(
                    title = "전체 릴레이",
                    onOn = { onRelayCommand(0, 1, 0) },
                    onOff = { onRelayCommand(0, 0, 0) }
                )
            }
        }
    }
}

@Composable
private fun RelayButtonRow(
    title: String,
    onOn: () -> Unit,
    onOff: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Button(onClick = onOn, modifier = Modifier.weight(1f)) {
            Text("ON")
        }
        Button(onClick = onOff, modifier = Modifier.weight(1f)) {
            Text("OFF")
        }
    }
}

@Composable
private fun DeviceCard(
    device: UsbDeviceSnapshot,
    onRequestPermission: () -> Unit
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(device.deviceName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    text = if (device.hasPermission) "권한 있음" else "권한 필요",
                    color = if (device.hasPermission) PassGreen else FailRed
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("VID ${device.vendorIdHex}", ActiveBlue)
                StatusChip("PID ${device.productIdHex}", ActiveBlue)
                StatusChip(device.typeGuess, PassGreen)
            }

            DetailGrid(
                items = listOf(
                    "제조사" to device.manufacturerName,
                    "제품명" to device.productName,
                    "장치 Class" to device.deviceClassName,
                    "Subclass" to device.deviceSubclass.toString(),
                    "Protocol" to device.deviceProtocol.toString(),
                    "Interface" to "${device.interfaces.size}개"
                )
            )

            if (!device.hasPermission) {
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("USB 권한 요청")
                }
            }

            device.interfaces.forEach { usbInterface ->
                InterfaceSection(usbInterface = usbInterface)
            }
        }
    }
}

@Composable
private fun InterfaceSection(usbInterface: UsbInterfaceSnapshot) {
    Surface(
        color = Color(0xFFF0F4F8),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Interface ${usbInterface.id}: ${usbInterface.className}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Class ${usbInterface.interfaceClass}, Subclass ${usbInterface.interfaceSubclass}, Protocol ${usbInterface.interfaceProtocol}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5B6472)
            )

            if (usbInterface.endpoints.isEmpty()) {
                Text("Endpoint 없음", style = MaterialTheme.typography.bodySmall)
            } else {
                usbInterface.endpoints.forEach { endpoint ->
                    Text(
                        text = "Endpoint ${endpoint.addressHex} / ${endpoint.directionName} / ${endpoint.typeName} / ${endpoint.maxPacketSize} bytes",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LogCard(
    logs: List<String>,
    title: String = "로그",
    limit: Int = 12
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (logs.isEmpty()) {
                Text("대기")
            } else {
                logs.take(limit).forEach { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun DetailGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5B6472))
                        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun UsbDevice.toSnapshot(hasPermission: Boolean): UsbDeviceSnapshot {
    val interfaceSnapshots = (0 until interfaceCount).map { index ->
        getInterface(index).toSnapshot()
    }

    return UsbDeviceSnapshot(
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        manufacturerName = safeValue { manufacturerName },
        productName = safeValue { productName },
        hasPermission = hasPermission,
        interfaces = interfaceSnapshots
    )
}

private fun UsbInterface.toSnapshot(): UsbInterfaceSnapshot {
    val endpoints = (0 until endpointCount).map { index ->
        getEndpoint(index).toSnapshot()
    }

    return UsbInterfaceSnapshot(
        id = id,
        interfaceClass = interfaceClass,
        interfaceSubclass = interfaceSubclass,
        interfaceProtocol = interfaceProtocol,
        endpoints = endpoints
    )
}

private fun UsbEndpoint.toSnapshot(): UsbEndpointSnapshot {
    return UsbEndpointSnapshot(
        address = address,
        attributes = attributes,
        maxPacketSize = maxPacketSize,
        direction = direction,
        type = type
    )
}

private fun UsbDevice.isAccessLinkSerialDevice(): Boolean {
    return vendorId == ACCESS_LINK_SERIAL_VENDOR_ID && productId == ACCESS_LINK_SERIAL_PRODUCT_ID
}

private fun Intent.usbDevice(): UsbDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}

private inline fun safeValue(block: () -> String?): String {
    return try {
        block().orEmpty().ifBlank { "미확인" }
    } catch (_: SecurityException) {
        "권한 필요"
    }
}

private fun buildDiagnosticFindings(
    serialDevice: UsbDeviceSnapshot?,
    lanDevice: UsbDeviceSnapshot?,
    serialState: SerialUiState,
    ethernetState: EthernetUiState,
    portState: PortDashboardState
): List<DiagnosticFinding> {
    val findings = mutableListOf<DiagnosticFinding>()

    when {
        serialDevice == null -> findings += DiagnosticFinding(
            level = "확정",
            area = "ACCESS LINK / USB Serial",
            title = "CH340 USB Serial 미감지",
            evidence = "VID 0x1A86 / PID 0x7523 장치가 Android USB 목록에 없음",
            nextStep = "폰과 ACCESS LINK USB-C 연결 상태를 확인",
            color = FailRed
        )

        !serialDevice.hasPermission -> findings += DiagnosticFinding(
            level = "확정",
            area = "앱 권한",
            title = "USB Serial 권한 없음",
            evidence = "CH340 장치는 감지됐지만 Android USB 권한이 없음",
            nextStep = "USB 권한 요청 승인",
            color = FailRed
        )

        serialState.connected -> findings += DiagnosticFinding(
            level = "정상",
            area = "USB Serial",
            title = "Serial 연결됨",
            evidence = serialState.status,
            nextStep = "Wiegand, RS-232, RS-485, Digital Input 수신 확인",
            color = PassGreen
        )

        else -> findings += DiagnosticFinding(
            level = "확인 필요",
            area = "앱 조작",
            title = "Serial 미연결",
            evidence = "CH340 장치와 권한은 확인됐지만 Serial 포트가 열려 있지 않음",
            nextStep = "9600bps 연결 실행",
            color = ActiveBlue
        )
    }

    when {
        lanDevice == null -> findings += DiagnosticFinding(
            level = "확정",
            area = "ACCESS LINK / USB LAN",
            title = "USB LAN 장치 미감지",
            evidence = "VID 0x0BDA / PID 0x8152 장치가 Android USB 목록에 없음",
            nextStep = "ACCESS LINK USB 연결 상태 확인",
            color = FailRed
        )

        ethernetState.connected -> findings += DiagnosticFinding(
            level = "정상",
            area = "Ethernet",
            title = "Ethernet 링크 감지",
            evidence = ethernetState.detail,
            nextStep = "네트워크 통신 테스트 진행",
            color = PassGreen
        )

        else -> findings += DiagnosticFinding(
            level = "확정",
            area = "Ethernet",
            title = "Ethernet 링크 미감지",
            evidence = "USB LAN 장치는 감지됐지만 Android Ethernet 링크가 없음",
            nextStep = "랜선 연결과 상대 장비 포트 링크 상태 확인",
            color = FailRed
        )
    }

    when {
        portState.lastWiegand != null -> findings += DiagnosticFinding(
            level = "정상",
            area = "Wiegand",
            title = "카드 데이터 확정",
            evidence = portState.lastWiegand,
            nextStep = "같은 카드 반복 태그로 값 일치 확인",
            color = PassGreen
        )

        portState.rawWiegandStatus != null -> findings += DiagnosticFinding(
            level = "확인 필요",
            area = "Wiegand",
            title = "카드 데이터 미확정",
            evidence = portState.rawWiegandStatus,
            nextStep = "같은 카드를 3회 태그하고 값 반복 일치 여부 확인",
            color = ActiveBlue
        )

        else -> findings += DiagnosticFinding(
            level = "확인 필요",
            area = "Wiegand",
            title = "Wiegand 수신 없음",
            evidence = "앱에서 Wiegand 수신 데이터를 아직 관측하지 못함",
            nextStep = "Serial 연결 후 카드 태그 또는 Wiegand 입력 조회",
            color = WaitGray
        )
    }

    if (portState.lastRs232 != null || portState.lastRs485 != null) {
        findings += DiagnosticFinding(
            level = "정상",
            area = "RS-232 / RS-485",
            title = "시리얼 포트 데이터 수신",
            evidence = "RS-232 ${portState.lastRs232 ?: "-"} / RS-485 ${portState.lastRs485 ?: "-"}",
            nextStep = "프로토콜별 payload 해석 확인",
            color = PassGreen
        )
    } else {
        findings += DiagnosticFinding(
            level = "확인 필요",
            area = "RS-232 / RS-485",
            title = "시리얼 포트 수신 없음",
            evidence = "앱에서 RS-232/RS-485 수신 데이터를 아직 관측하지 못함",
            nextStep = "상대 장비 송신 상태와 포트 선택 확인",
            color = WaitGray
        )
    }

    if (portState.input0 != null || portState.input1 != null) {
        findings += DiagnosticFinding(
            level = "정상",
            area = "Digital Input",
            title = "Digital Input 상태 수신",
            evidence = "IN0 ${portState.input0 ?: "-"} / IN1 ${portState.input1 ?: "-"}",
            nextStep = "입력 접점 ON/OFF 변화 확인",
            color = PassGreen
        )
    } else {
        findings += DiagnosticFinding(
            level = "확인 필요",
            area = "Digital Input",
            title = "Digital Input 상태 미수신",
            evidence = "앱에서 IN0/IN1 상태 데이터를 아직 관측하지 못함",
            nextStep = "입력 조회 또는 접점 변화 테스트",
            color = WaitGray
        )
    }

    if (portState.lastRelayHex != null) {
        findings += DiagnosticFinding(
            level = "정상",
            area = "Relay",
            title = "Relay 명령 송신 기록 있음",
            evidence = portState.lastRelayHex,
            nextStep = "외부 부하 동작 여부는 현장에서 별도 확인",
            color = PassGreen
        )
    } else {
        findings += DiagnosticFinding(
            level = "확인 필요",
            area = "Relay",
            title = "Relay 명령 송신 기록 없음",
            evidence = "앱 실행 후 Relay ON/OFF 명령 기록이 없음",
            nextStep = "Relay 1 또는 Relay 2 ON/OFF 테스트",
            color = WaitGray
        )
    }

    return findings
}

private data class DiagnosticFinding(
    val level: String,
    val area: String,
    val title: String,
    val evidence: String,
    val nextStep: String,
    val color: Color
)

private data class SerialUiState(
    val connected: Boolean = false,
    val baudRate: Int = 9600,
    val status: String = "연결 안 됨"
)

private data class EthernetUiState(
    val connected: Boolean = false,
    val interfaceName: String? = null,
    val detail: String = "랜선 링크 미감지"
)

private data class PortDashboardState(
    val lastWiegand: String? = null,
    val rawWiegandStatus: String? = null,
    val lastCardHex: String? = null,
    val lastRs232: String? = null,
    val lastRs485: String? = null,
    val input0: String? = null,
    val input1: String? = null,
    val relay0: String? = null,
    val relay1: String? = null,
    val lastRelayHex: String? = null,
    val lastRawHex: String? = null
)

private data class UsbDeviceSnapshot(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val manufacturerName: String,
    val productName: String,
    val hasPermission: Boolean,
    val interfaces: List<UsbInterfaceSnapshot>
) {
    val displayName: String
        get() = when {
            isAccessLinkLan -> "ACCESS LINK USB LAN"
            isAccessLinkSerial -> "ACCESS LINK USB Serial"
            productName != "미확인" && productName != "권한 필요" -> productName
            manufacturerName != "미확인" && manufacturerName != "권한 필요" -> manufacturerName
            else -> "USB 장치"
        }

    val vendorIdHex: String get() = vendorId.toUsbHex()
    val productIdHex: String get() = productId.toUsbHex()
    val deviceClassName: String get() = usbClassName(deviceClass)
    val isAccessLinkSerial: Boolean
        get() = vendorId == ACCESS_LINK_SERIAL_VENDOR_ID && productId == ACCESS_LINK_SERIAL_PRODUCT_ID
    val isAccessLinkLan: Boolean
        get() = vendorId == ACCESS_LINK_LAN_VENDOR_ID && productId == ACCESS_LINK_LAN_PRODUCT_ID

    val typeGuess: String
        get() {
            val classes = interfaces.map { it.interfaceClass }.toSet() + deviceClass
            return when {
                isAccessLinkSerial -> "CH340 USB Serial"
                isAccessLinkLan -> "USB Ethernet/LAN"
                UsbConstants.USB_CLASS_COMM in classes || UsbConstants.USB_CLASS_CDC_DATA in classes -> "Serial/CDC 추정"
                UsbConstants.USB_CLASS_HID in classes -> "HID 추정"
                UsbConstants.USB_CLASS_MISC in classes || interfaces.size > 1 -> "Composite 추정"
                UsbConstants.USB_CLASS_VENDOR_SPEC in classes -> "Vendor 전용"
                else -> "타입 확인 필요"
            }
        }
}

private data class UsbInterfaceSnapshot(
    val id: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpoints: List<UsbEndpointSnapshot>
) {
    val className: String get() = usbClassName(interfaceClass)
}

private data class UsbEndpointSnapshot(
    val address: Int,
    val attributes: Int,
    val maxPacketSize: Int,
    val direction: Int,
    val type: Int
) {
    val addressHex: String get() = "0x${address.toString(16).uppercase(Locale.US).padStart(2, '0')}"
    val directionName: String get() = if (direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
    val typeName: String
        get() = when (type) {
            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "Control"
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "Isochronous"
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "Bulk"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "Interrupt"
            else -> "Unknown"
        }
}

private fun Int.toUsbHex(): String {
    return "0x${toString(16).uppercase(Locale.US).padStart(4, '0')}"
}

private fun ByteArray.toDisplayText(): String {
    val asciiText = map { it.toInt() and 0xFF }
        .takeIf { values -> values.all { it in 0x20..0x7E } }
        ?.map { it.toChar() }
        ?.joinToString("")
    return asciiText?.ifBlank { null } ?: toHexString()
}

private fun ByteArray.toInputStatus(): String {
    val value = firstOrNull()?.toInt()?.and(0xFF) ?: return "미확인"
    return when (value) {
        0, '0'.code -> "Released"
        1, '1'.code -> "Pressed"
        else -> "Unknown ${toHexString()}"
    }
}

private fun usbClassName(value: Int): String {
    return when (value) {
        UsbConstants.USB_CLASS_APP_SPEC -> "Application Specific"
        UsbConstants.USB_CLASS_AUDIO -> "Audio"
        UsbConstants.USB_CLASS_CDC_DATA -> "CDC Data"
        UsbConstants.USB_CLASS_COMM -> "Communication"
        UsbConstants.USB_CLASS_CONTENT_SEC -> "Content Security"
        UsbConstants.USB_CLASS_CSCID -> "Smart Card"
        UsbConstants.USB_CLASS_HID -> "HID"
        UsbConstants.USB_CLASS_HUB -> "Hub"
        UsbConstants.USB_CLASS_MASS_STORAGE -> "Mass Storage"
        UsbConstants.USB_CLASS_MISC -> "Miscellaneous"
        UsbConstants.USB_CLASS_PER_INTERFACE -> "Per Interface"
        UsbConstants.USB_CLASS_PHYSICA -> "Physical"
        UsbConstants.USB_CLASS_PRINTER -> "Printer"
        UsbConstants.USB_CLASS_STILL_IMAGE -> "Still Image"
        UsbConstants.USB_CLASS_VENDOR_SPEC -> "Vendor Specific"
        UsbConstants.USB_CLASS_VIDEO -> "Video"
        UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "Wireless Controller"
        else -> "Unknown ($value)"
    }
}

private val PassGreen = Color(0xFF138A3D)
private val FailRed = Color(0xFFC62828)
private val ActiveBlue = Color(0xFF1565C0)
private val WaitGray = Color(0xFF6B7280)

@Preview(showBackground = true)
@Composable
private fun UsbDiagnosticPreview() {
    val previewDevice = UsbDeviceSnapshot(
        deviceName = "/dev/bus/usb/001/002",
        vendorId = 0x1234,
        productId = 0x5678,
        deviceClass = UsbConstants.USB_CLASS_PER_INTERFACE,
        deviceSubclass = 0,
        deviceProtocol = 0,
        manufacturerName = "SHINHWA SYSTEM",
        productName = "ACCESS LINK",
        hasPermission = true,
        interfaces = listOf(
            UsbInterfaceSnapshot(
                id = 0,
                interfaceClass = UsbConstants.USB_CLASS_COMM,
                interfaceSubclass = 2,
                interfaceProtocol = 1,
                endpoints = listOf(
                    UsbEndpointSnapshot(0x81, 0x02, 64, UsbConstants.USB_DIR_IN, UsbConstants.USB_ENDPOINT_XFER_BULK),
                    UsbEndpointSnapshot(0x02, 0x02, 64, UsbConstants.USB_DIR_OUT, UsbConstants.USB_ENDPOINT_XFER_BULK)
                )
            )
        )
    )

    AccessLinkTesterTheme(dynamicColor = false) {
        UsbDiagnosticApp(
            devices = listOf(previewDevice),
            serialState = SerialUiState(connected = true, baudRate = 9600, status = "연결됨: 9600bps"),
            ethernetState = EthernetUiState(connected = true, interfaceName = "eth0", detail = "eth0 / 192.168.0.10"),
            portState = PortDashboardState(
                lastWiegand = "32bit / Decimal 131654 / Data 00 02 02 46",
                input0 = "Released",
                input1 = "Pressed",
                lastRawHex = "00 02 02 46"
            ),
            logs = listOf("[12:00:00] 미리보기 로그"),
            adminMode = false,
            onRefresh = {},
            onAdminToggle = {},
            onRequestPermission = {},
            onConnectSerial = {},
            onDisconnectSerial = {},
            onWiegandQuery = {},
            onRelayCommand = { _, _, _ -> }
        )
    }
}
