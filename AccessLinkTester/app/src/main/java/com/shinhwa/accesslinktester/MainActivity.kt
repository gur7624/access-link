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
            appendLog("제어 장치 없음")
            serialState.value = serialState.value.copy(status = "장치 없음")
            return
        }

        if (!usbManager.hasPermission(device)) {
            appendLog("권한 필요")
            requestUsbPermission(device.toSnapshot(false))
            serialState.value = serialState.value.copy(status = "권한 필요")
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
            appendLog("연결 해제")
        }
        serialState.value = serialState.value.copy(connected = false, status = "연결 안 됨")
    }

    private fun sendRelayCommand(useRelay: Int, outputType: Int, time: Int) {
        val packet = AccessLinkProtocol.relayControl(useRelay, outputType, time)
        try {
            val controller = serialController ?: error("연결 필요")
            controller.write(packet)
            val relayState = if (outputType == 1) "ON 송신" else "OFF 송신"
            portState.value = portState.value.copy(
                relay0 = if (useRelay == 0 || useRelay == 1) relayState else portState.value.relay0,
                relay1 = if (useRelay == 0 || useRelay == 2) relayState else portState.value.relay1,
                lastRelayHex = packet.toHexString()
            )
            appendLog("송신 ${packet.toHexString()}")
        } catch (exception: Exception) {
            appendLog("Relay 실패: ${exception.message ?: "알 수 없는 오류"}")
        }
    }

    private fun sendWiegandQuery() {
        val packet = AccessLinkProtocol.getWiegandInputData()
        try {
            val controller = serialController ?: error("연결 필요")
            controller.write(packet)
            raw32Buffer.clear()
            appendLog("Wiegand 조회 ${packet.toHexString()}")
        } catch (exception: Exception) {
            appendLog("Wiegand 실패: ${exception.message ?: "알 수 없는 오류"}")
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
                    rawWiegandStatus = "반복 확인 ${raw32.summary}",
                    lastRawHex = rawHex
                )
            }
            return if (stableRaw32) {
                "수신 $rawHex / 카드 확인: ${raw32.summary}"
            } else {
                "수신 $rawHex / 반복 확인: ${raw32.summary}"
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
            "수신 $rawHex / 조각 수신: ${raw32Buffer.size}/4 bytes"
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
                        text = "Access Link",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
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
                StatusChip(
                    text = if (connectedCount > 0) "연결" else "미연결",
                    color = if (connectedCount > 0) PassGreen else WaitGray
                )
                StatusChip(
                    text = if (permissionCount > 0) "권한" else "권한 필요",
                    color = if (permissionCount > 0) ActiveBlue else WaitGray
                )
            }
        }
    }
}

@Composable
private fun StatusSummary(devices: List<UsbDeviceSnapshot>) {
    val title = when {
        devices.isEmpty() -> "USB 장치가 아직 감지되지 않았습니다"
        devices.any { it.hasPermission } -> "연결"
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
                Text("관리자", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                findings.forEach { finding ->
                    DiagnosticFindingRow(finding)
                }
            }
        }

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RAW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DetailGrid(
                    items = listOf(
                        "Baudrate" to serialState.baudRate.toString(),
                        "Serial" to serialState.status,
                        "Ethernet" to ethernetState.detail,
                        "카드 HEX" to (portState.lastCardHex ?: "-"),
                        "HEX" to (portState.lastRawHex ?: "-"),
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

        LogCard(logs = logs, title = "로그", limit = 80)
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
            Text(finding.area, style = MaterialTheme.typography.bodySmall)
            Text(finding.evidence, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
            Text(finding.nextStep, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
        }
    }
}

@Composable
private fun EmptyDeviceCard() {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("장치 없음", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("USB-C 연결")
        }
    }
}

@Composable
private fun FieldPortDashboard(
    serialDevice: UsbDeviceSnapshot?,
    serialState: SerialUiState,
    ethernetState: EthernetUiState,
    portState: PortDashboardState
) {
    val cardConfirmed = portState.lastWiegand != null
    val digitalConfirmed = portState.input0 != null || portState.input1 != null
    val rs232Confirmed = portState.lastRs232 != null
    val rs485Confirmed = portState.lastRs485 != null

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("상태", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            PortStatusRow(
                title = "제어 연결",
                description = "",
                value = when {
                    serialDevice == null -> "장치 없음"
                    !serialDevice.hasPermission -> "권한 필요"
                    else -> serialState.status
                },
                status = when {
                    serialDevice == null -> "미연결"
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
                title = "Ethernet",
                description = "",
                value = ethernetState.detail,
                status = if (ethernetState.connected) "연결" else "끊김",
                color = if (ethernetState.connected) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Wiegand IN",
                description = "",
                value = portState.rawWiegandStatus ?: portState.lastCardHex ?: "수신 없음",
                status = when {
                    cardConfirmed -> "수신"
                    portState.rawWiegandStatus != null -> "수신"
                    else -> "수신 없음"
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
                value = portState.lastWiegand ?: "-",
                status = if (cardConfirmed) "확인" else "-",
                color = if (cardConfirmed) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Input",
                description = "",
                value = "1 ${portState.input0 ?: "-"} / 2 ${portState.input1 ?: "-"}",
                status = if (digitalConfirmed) "확인" else "-",
                color = if (digitalConfirmed) ActiveBlue else WaitGray
            )

            PortStatusRow(
                title = "RS-232",
                description = "",
                value = portState.lastRs232 ?: "수신 없음",
                status = if (rs232Confirmed) "수신" else "수신 없음",
                color = if (rs232Confirmed) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "RS-485",
                description = "",
                value = portState.lastRs485 ?: "수신 없음",
                status = if (rs485Confirmed) "수신" else "수신 없음",
                color = if (rs485Confirmed) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Relay Output",
                description = "",
                value = "1 ${portState.relay0 ?: "-"} / 2 ${portState.relay1 ?: "-"}",
                status = if (serialState.connected) "제어 가능" else "연결 필요",
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
                    text = if (serialState.connected) "연결" else "대기",
                    color = if (serialState.connected) PassGreen else WaitGray
                )
            }

            if (serialDevice == null) {
                Text("제어 장치 없음")
                return@InfoCard
            }

            if (!serialDevice.hasPermission) {
                Button(onClick = { onRequestPermission(serialDevice) }, modifier = Modifier.fillMaxWidth()) {
                    Text("권한 요청")
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

            if (serialState.connected) {
                Button(onClick = onDisconnectSerial, modifier = Modifier.fillMaxWidth()) {
                    Text("연결 해제")
                }

                Button(onClick = onWiegandQuery, modifier = Modifier.fillMaxWidth()) {
                    Text("Wiegand 조회")
                }

                Text("Relay Output", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                RelayButtonRow(
                    title = "Relay 1",
                    onOn = { onRelayCommand(1, 1, 0) },
                    onOff = { onRelayCommand(1, 0, 0) }
                )
                RelayButtonRow(
                    title = "Relay 2",
                    onOn = { onRelayCommand(2, 1, 0) },
                    onOff = { onRelayCommand(2, 0, 0) }
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
            level = "문제",
            area = "USB-C",
            title = "제어 장치 없음",
            evidence = "VID 0x1A86 / PID 0x7523 없음",
            nextStep = "폰과 Access Link USB-C 확인",
            color = FailRed
        )

        !serialDevice.hasPermission -> findings += DiagnosticFinding(
            level = "문제",
            area = "권한",
            title = "권한 없음",
            evidence = "CH340 감지됨",
            nextStep = "권한 요청 승인",
            color = FailRed
        )

        serialState.connected -> findings += DiagnosticFinding(
            level = "정상",
            area = "제어 연결",
            title = "연결",
            evidence = serialState.status,
            nextStep = "Wiegand / RS-232 / RS-485 확인",
            color = PassGreen
        )

        else -> findings += DiagnosticFinding(
            level = "확인",
            area = "제어 연결",
            title = "미연결",
            evidence = "CH340 권한 있음",
            nextStep = "9600 연결",
            color = ActiveBlue
        )
    }

    when {
        lanDevice == null -> findings += DiagnosticFinding(
            level = "문제",
            area = "Ethernet",
            title = "LAN 장치 없음",
            evidence = "VID 0x0BDA / PID 0x8152 없음",
            nextStep = "USB-C 연결 확인",
            color = FailRed
        )

        ethernetState.connected -> findings += DiagnosticFinding(
            level = "정상",
            area = "Ethernet",
            title = "연결",
            evidence = ethernetState.detail,
            nextStep = "통신 확인",
            color = PassGreen
        )

        else -> findings += DiagnosticFinding(
            level = "문제",
            area = "Ethernet",
            title = "끊김",
            evidence = "LAN 장치 감지됨",
            nextStep = "랜선 / 포트 링크 확인",
            color = FailRed
        )
    }

    when {
        portState.lastWiegand != null -> findings += DiagnosticFinding(
            level = "정상",
            area = "Wiegand IN",
            title = "카드 수신",
            evidence = portState.lastWiegand,
            nextStep = "반복 태그 확인",
            color = PassGreen
        )

        portState.rawWiegandStatus != null -> findings += DiagnosticFinding(
            level = "확인",
            area = "Wiegand IN",
            title = "조각 수신",
            evidence = portState.rawWiegandStatus,
            nextStep = "반복 태그 확인",
            color = ActiveBlue
        )

        else -> findings += DiagnosticFinding(
            level = "확인",
            area = "Wiegand IN",
            title = "수신 없음",
            evidence = "-",
            nextStep = "카드 태그 / Wiegand 조회",
            color = WaitGray
        )
    }

    if (portState.lastRs232 != null || portState.lastRs485 != null) {
        findings += DiagnosticFinding(
            level = "정상",
            area = "RS-232 / RS-485",
            title = "수신",
            evidence = "RS-232 ${portState.lastRs232 ?: "-"} / RS-485 ${portState.lastRs485 ?: "-"}",
            nextStep = "데이터 확인",
            color = PassGreen
        )
    } else {
        findings += DiagnosticFinding(
            level = "확인",
            area = "RS-232 / RS-485",
            title = "수신 없음",
            evidence = "-",
            nextStep = "결선 / 상대 장비 송신 확인",
            color = WaitGray
        )
    }

    if (portState.input0 != null || portState.input1 != null) {
        findings += DiagnosticFinding(
            level = "정상",
            area = "Input",
            title = "수신",
            evidence = "1 ${portState.input0 ?: "-"} / 2 ${portState.input1 ?: "-"}",
            nextStep = "ON/OFF 확인",
            color = PassGreen
        )
    } else {
        findings += DiagnosticFinding(
            level = "확인",
            area = "Input",
            title = "수신 없음",
            evidence = "-",
            nextStep = "입력 변화 확인",
            color = WaitGray
        )
    }

    if (portState.lastRelayHex != null) {
        findings += DiagnosticFinding(
            level = "정상",
            area = "Relay Output",
            title = "제어 기록",
            evidence = portState.lastRelayHex,
            nextStep = "출력 확인",
            color = PassGreen
        )
    } else {
        findings += DiagnosticFinding(
            level = "확인",
            area = "Relay Output",
            title = "제어 기록 없음",
            evidence = "-",
            nextStep = "Relay 1 / 2 테스트",
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
                lastWiegand = "Decimal 131654 / Data 00 02 02 46",
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
