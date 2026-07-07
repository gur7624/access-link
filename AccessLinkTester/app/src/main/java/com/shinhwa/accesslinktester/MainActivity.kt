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

class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbManager
    private val usbDevices = mutableStateListOf<UsbDeviceSnapshot>()
    private val logs = mutableStateListOf<String>()
    private val serialState = mutableStateOf(SerialUiState())
    private val portState = mutableStateOf(PortDashboardState())
    private var serialController: AccessLinkSerialController? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(UsbManager::class.java)
        registerUsbReceiver()
        refreshUsbDevices()
        enableEdgeToEdge()

        setContent {
            AccessLinkTesterTheme(dynamicColor = false) {
                UsbDiagnosticApp(
                    devices = usbDevices,
                    serialState = serialState.value,
                    portState = portState.value,
                    logs = logs,
                    onRefresh = {
                        appendLog("USB 장치 목록 새로고침")
                        refreshUsbDevices()
                    },
                    onRequestPermission = ::requestUsbPermission,
                    onConnectSerial = ::connectSerial,
                    onDisconnectSerial = ::disconnectSerial,
                    onRelayCommand = ::sendRelayCommand
                )
            }
        }
    }

    override fun onDestroy() {
        disconnectSerial()
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
                        updatePortState(data)
                        appendLog("수신 ${data.toHexString()} / ${AccessLinkProtocol.describeIncoming(data)}")
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
            appendLog("송신 ${packet.toHexString()}")
        } catch (exception: Exception) {
            appendLog("릴레이 명령 실패: ${exception.message ?: "알 수 없는 오류"}")
        }
    }

    private fun updatePortState(data: ByteArray) {
        if (data.size < 4 || data.first() != 0x02.toByte() || data.last() != 0x03.toByte()) {
            portState.value = portState.value.copy(lastRawHex = data.toHexString())
            return
        }

        val command = data[2].toInt() and 0xFF
        val payload = data.copyOfRange(3, data.size - 1)
        val rawHex = data.toHexString()
        portState.value = when (command) {
            AccessLinkProtocol.CMD_GET_WIEGAND_INPUT_DATA -> {
                val bitLength = when (payload.size) {
                    8 -> "26bit"
                    10 -> "34bit"
                    else -> "${payload.size} bytes"
                }
                portState.value.copy(
                    lastWiegand = "${payload.toDisplayText()} ($bitLength)",
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

@Composable
private fun UsbDiagnosticApp(
    devices: List<UsbDeviceSnapshot>,
    serialState: SerialUiState,
    portState: PortDashboardState,
    logs: List<String>,
    onRefresh: () -> Unit,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
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
                    onRefresh = onRefresh
                )
            }

            item {
                StatusSummary(devices = devices)
            }

            item {
                FieldPortDashboard(
                    serialDevice = devices.firstOrNull { it.isAccessLinkSerial },
                    lanDevice = devices.firstOrNull { it.isAccessLinkLan },
                    serialState = serialState,
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
                    onRelayCommand = onRelayCommand
                )
            }

            if (devices.isEmpty()) {
                item {
                    EmptyDeviceCard()
                }
            } else {
                items(devices, key = { it.deviceName }) { device ->
                    DeviceCard(
                        device = device,
                        onRequestPermission = { onRequestPermission(device) }
                    )
                }
            }

            item {
                LogCard(logs = logs)
            }
        }
    }
}

@Composable
private fun HeaderCard(
    connectedCount: Int,
    permissionCount: Int,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102033))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ACCESS LINK USB 진단",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "현장 장비 인식 상태를 확인합니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC7D2E1)
                    )
                }
                Button(onClick = onRefresh) {
                    Text("새로고침")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("감지 $connectedCount", if (connectedCount > 0) PassGreen else WaitGray)
                StatusChip("권한 $permissionCount", if (permissionCount > 0) ActiveBlue else WaitGray)
                StatusChip(
                    text = if (connectedCount > 0) "확인 필요" else "대기",
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
                Text("전체 상태", style = MaterialTheme.typography.labelLarge, color = Color(0xFF5B6472))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            StatusChip(text = if (devices.any { it.hasPermission }) "PASS" else "CHECK", color = color)
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
    portState: PortDashboardState
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("현장 포트 대시보드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "장비 상단 표기 기준으로 Wiegand, Relay, Digital Input, RS-232/485, Ethernet 상태를 확인합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5B6472)
            )

            PortStatusRow(
                title = "USB-C 제어 통신",
                description = "CH340 Serial, 릴레이/입력/리더기 데이터 통신",
                value = serialState.status,
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
                title = "Ethernet",
                description = "Realtek USB LAN, 네트워크 통신 확인용",
                value = lanDevice?.let { "${it.vendorIdHex}:${it.productIdHex}" } ?: "장치 미감지",
                status = if (lanDevice != null) "감지" else "미감지",
                color = if (lanDevice != null) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Wiegand IN",
                description = "리더기 D0/D1/GND 입력, 26/34bit 카드 수신",
                value = portState.lastWiegand ?: "카드 태그 대기",
                status = if (portState.lastWiegand != null) "수신" else "대기",
                color = if (portState.lastWiegand != null) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Digital Input",
                description = "IN0+/IN0-, IN1+/IN1- 접점 입력",
                value = "IN0 ${portState.input0 ?: "미확인"} / IN1 ${portState.input1 ?: "미확인"}",
                status = if (portState.input0 != null || portState.input1 != null) "변화 감지" else "대기",
                color = if (portState.input0 != null || portState.input1 != null) ActiveBlue else WaitGray
            )

            PortStatusRow(
                title = "RS-232 / RS-485",
                description = "외부 시리얼 장비 송수신 로그",
                value = "232 ${portState.lastRs232 ?: "-"} / 485 ${portState.lastRs485 ?: "-"}",
                status = if (portState.lastRs232 != null || portState.lastRs485 != null) "수신" else "대기",
                color = if (portState.lastRs232 != null || portState.lastRs485 != null) PassGreen else WaitGray
            )

            PortStatusRow(
                title = "Relay Output 1/2",
                description = "NC/COM/NO 접점 출력, 앱 버튼으로 ON/OFF 테스트",
                value = "릴레이 명령은 아래 제어 테스트에서 실행",
                status = if (serialState.connected) "테스트 가능" else "Serial 필요",
                color = if (serialState.connected) ActiveBlue else WaitGray
            )

            if (portState.lastRawHex != null) {
                Surface(
                    color = Color(0xFFF0F4F8),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("마지막 원본 수신 HEX", style = MaterialTheme.typography.labelLarge, color = Color(0xFF5B6472))
                        Text(portState.lastRawHex, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
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
                    Text("ACCESS LINK 제어 테스트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(serialState.status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B6472))
                }
                StatusChip(
                    text = if (serialState.connected) "Serial 연결" else "대기",
                    color = if (serialState.connected) PassGreen else WaitGray
                )
            }

            if (serialDevice == null) {
                Text("CH340 USB Serial 장치가 감지되면 릴레이 테스트를 시작할 수 있습니다.")
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
                    Text("9600 연결")
                }
                Button(onClick = { onConnectSerial(115200) }, modifier = Modifier.weight(1f)) {
                    Text("115200 연결")
                }
            }

            if (serialState.connected) {
                Button(onClick = onDisconnectSerial, modifier = Modifier.fillMaxWidth()) {
                    Text("Serial 연결 해제")
                }

                Text("릴레이 테스트", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
private fun LogCard(logs: List<String>) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("실시간 로그", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (logs.isEmpty()) {
                Text("아직 로그가 없습니다.")
            } else {
                logs.take(12).forEach { log ->
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

private data class SerialUiState(
    val connected: Boolean = false,
    val baudRate: Int = 9600,
    val status: String = "연결 안 됨"
)

private data class PortDashboardState(
    val lastWiegand: String? = null,
    val lastRs232: String? = null,
    val lastRs485: String? = null,
    val input0: String? = null,
    val input1: String? = null,
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
            portState = PortDashboardState(
                lastWiegand = "12345678 (26bit)",
                input0 = "Released",
                input1 = "Pressed",
                lastRawHex = "02 0C 09 31 32 33 34 35 36 37 38 03"
            ),
            logs = listOf("[12:00:00] 미리보기 로그"),
            onRefresh = {},
            onRequestPermission = {},
            onConnectSerial = {},
            onDisconnectSerial = {},
            onRelayCommand = { _, _, _ -> }
        )
    }
}
