package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.shinhwa.accesslinktester.AccessLinkAppController
import com.shinhwa.accesslinktester.SerialInputMode
import com.shinhwa.accesslinktester.SerialPort
import com.shinhwa.accesslinktester.label
import com.shinhwa.accesslinktester.model.UsbDeviceSnapshot
import com.shinhwa.accesslinktester.ui.components.DetailGrid
import com.shinhwa.accesslinktester.ui.components.InfoCard
import com.shinhwa.accesslinktester.ui.components.SectionLabel
import com.shinhwa.accesslinktester.ui.components.StatusChip
import com.shinhwa.accesslinktester.ui.theme.ActiveBlue
import com.shinhwa.accesslinktester.ui.theme.FailRed
import com.shinhwa.accesslinktester.ui.theme.PassGreen
import com.shinhwa.accesslinktester.ui.theme.WaitGray

/**
 * 장비 진단 (설치·A/S용). USB/Ethernet 상태, RS-232/485 송수신, Wiegand 조회/출력, USB 장치 목록.
 */
@Composable
fun DeviceSection(
    controller: AccessLinkAppController,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit,
    onRefreshDevices: () -> Unit
) {
    var route by remember { mutableStateOf(DeviceDiagnosticRoute.HOME) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val currentRoute = route) {
            DeviceDiagnosticRoute.HOME -> DeviceDiagnosticHome(
                controller = controller,
                onNavigate = { route = it }
            )

            else -> {
                DiagnosticPageHeader(
                    title = currentRoute.title,
                    onBack = { route = DeviceDiagnosticRoute.HOME }
                )
                when (currentRoute) {
                    DeviceDiagnosticRoute.CONNECTION -> {
                        ConnectionCard(controller, onConnectSerial, onDisconnectSerial, onRefreshDevices)
                        UsbDeviceListCard(controller, onRequestPermission)
                    }

                    DeviceDiagnosticRoute.RELAY -> RelayDiagnosticsCard(controller)
                    DeviceDiagnosticRoute.WIEGAND -> WiegandDiagnosticsCard(controller)
                    DeviceDiagnosticRoute.RS232 -> SerialDiagnosticsCard(controller, SerialPort.RS232)
                    DeviceDiagnosticRoute.RS485 -> SerialDiagnosticsCard(controller, SerialPort.RS485)
                    DeviceDiagnosticRoute.HOME -> Unit
                }
            }
        }
    }
}

private enum class DeviceDiagnosticRoute(val title: String) {
    HOME("장비 진단"),
    CONNECTION("장비 연결"),
    RELAY("릴레이 테스트"),
    WIEGAND("카드 리더기 데이터"),
    RS232("RS-232 통신"),
    RS485("RS-485 통신")
}

@Composable
private fun DeviceDiagnosticHome(
    controller: AccessLinkAppController,
    onNavigate: (DeviceDiagnosticRoute) -> Unit
) {
    val serial = controller.serialState
    val diagnostics = controller.diagnostics

    SectionLabel("현장 장비 점검")
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("무엇을 확인하시겠습니까?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "기능별 화면에서 연결 상태와 수신·송신 결과를 자세히 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DetailGrid(
                items = listOf(
                    "장비 통신" to if (serial.connected) "연결됨" else "연결 필요",
                    "USB 장치" to "${controller.usbDevices.size}개",
                    "최근 카드" to (diagnostics.lastCardHex ?: "없음"),
                    "최근 릴레이" to (diagnostics.lastRelayHex ?: "없음")
                )
            )
        }
    }

    DiagnosticMenuButton(
        title = "장비 연결",
        description = "USB 권한, CH340, 통신 속도, 네트워크 링크",
        onClick = { onNavigate(DeviceDiagnosticRoute.CONNECTION) }
    )
    DiagnosticMenuButton(
        title = "릴레이 테스트",
        description = "Relay 0·1에 지정 시간 ON 명령을 보내 배선을 확인",
        onClick = { onNavigate(DeviceDiagnosticRoute.RELAY) }
    )
    DiagnosticMenuButton(
        title = "카드 리더기 데이터",
        description = "Wiegand 카드번호, Raw HEX, 수신 횟수 확인",
        onClick = { onNavigate(DeviceDiagnosticRoute.WIEGAND) }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DiagnosticMenuButton(
            title = "RS-232 통신",
            description = "ASCII·HEX 송수신",
            onClick = { onNavigate(DeviceDiagnosticRoute.RS232) },
            modifier = Modifier.weight(1f)
        )
        DiagnosticMenuButton(
            title = "RS-485 통신",
            description = "ASCII·HEX 송수신",
            onClick = { onNavigate(DeviceDiagnosticRoute.RS485) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DiagnosticPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = onBack) { Text("뒤로") }
    }
}

@Composable
private fun DiagnosticMenuButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 78.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RelayDiagnosticsCard(controller: AccessLinkAppController) {
    val connected = controller.serialState.connected
    var selectedRelay by remember { mutableStateOf(1) }
    var seconds by remember { mutableStateOf("3") }
    var statusText by remember { mutableStateOf("대기") }
    val pulseSeconds = seconds.toIntOrNull()?.coerceIn(1, 99) ?: 3

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("릴레이 진단", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "현장 배선 확인용입니다. 실제 릴레이 상태를 단정하지 않고 송신 결과만 표시합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(statusText, if (statusText.contains("실패")) FailRed else ActiveBlue)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RelaySelectButton("Relay 0", selectedRelay == 1, { selectedRelay = 1 }, Modifier.weight(1f))
                RelaySelectButton("Relay 1", selectedRelay == 2, { selectedRelay = 2 }, Modifier.weight(1f))
                RelaySelectButton("전체", selectedRelay == 0, { selectedRelay = 0 }, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it.filter { c -> c.isDigit() }.take(2) },
                modifier = Modifier.fillMaxWidth(),
                enabled = connected,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("테스트 출력 시간 (초)") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { statusText = controller.sendRelayControl(selectedRelay, outputType = 1, time = pulseSeconds) },
                    modifier = Modifier.weight(1f),
                    enabled = connected
                ) { Text("${pulseSeconds}초 ON") }
                OutlinedButton(
                    onClick = { statusText = controller.sendRelayControl(selectedRelay, outputType = 1, time = 0) },
                    modifier = Modifier.weight(1f),
                    enabled = connected
                ) { Text("지속 ON") }
                OutlinedButton(
                    onClick = { statusText = controller.sendRelayControl(selectedRelay, outputType = 0, time = 0) },
                    modifier = Modifier.weight(1f),
                    enabled = connected
                ) { Text("OFF") }
            }

            DetailGrid(
                items = listOf(
                    "마지막 릴레이 패킷" to (controller.diagnostics.lastRelayHex ?: "-"),
                    "선택" to relayLabel(selectedRelay)
                )
            )

            if (controller.relayLogs.isNotEmpty()) {
                controller.relayLogs.take(5).forEach { log ->
                    Text(
                        "${log.relay} · ${log.command} · ${log.packetHex}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RelaySelectButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

private fun relayLabel(value: Int): String {
    return when (value) {
        0 -> "Relay 0+1"
        1 -> "Relay 0"
        2 -> "Relay 1"
        else -> "-"
    }
}

@Composable
private fun ConnectionCard(
    controller: AccessLinkAppController,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
    onRefreshDevices: () -> Unit
) {
    val serial = controller.serialState
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("장비 연결", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(
                    text = if (serial.connected) "연결" else "미연결",
                    color = if (serial.connected) PassGreen else WaitGray
                )
            }
            DetailGrid(
                items = listOf(
                    "통신 속도" to serial.baudRate.toString(),
                    "상태" to serial.status,
                    "네트워크 링크" to controller.ethernetState.detail,
                    "USB 장치" to "${controller.usbDevices.size}개"
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onConnectSerial(9600) }, modifier = Modifier.weight(1f)) { Text("9600으로 연결") }
                Button(onClick = { onConnectSerial(115200) }, modifier = Modifier.weight(1f)) { Text("115200으로 연결") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRefreshDevices, modifier = Modifier.weight(1f)) { Text("새로고침") }
                if (serial.connected) {
                    OutlinedButton(onClick = onDisconnectSerial, modifier = Modifier.weight(1f)) { Text("연결 해제") }
                }
            }
            if (serial.connected) {
                Button(onClick = { controller.queryWiegand() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Wiegand 조회")
                }
            }
        }
    }
}

@Composable
private fun WiegandDiagnosticsCard(controller: AccessLinkAppController) {
    val diag = controller.diagnostics
    var outputText by remember { mutableStateOf("") }
    var useParity by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("대기") }
    val connected = controller.serialState.connected

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("카드 리더기 데이터 (Wiegand)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DetailGrid(
                items = listOf(
                    "Received" to (diag.lastCardHex ?: "-"),
                    "Raw" to (diag.lastWiegandRawHex ?: "-"),
                    "수신 시간" to (diag.lastWiegandReceivedAt ?: "-"),
                    "Count" to diag.wiegandReceiveCount.toString(),
                    "마지막" to (diag.lastWiegand ?: diag.rawWiegandStatus ?: "-")
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { controller.queryWiegand() },
                    modifier = Modifier.weight(1f),
                    enabled = connected
                ) { Text("조회") }
                OutlinedButton(
                    onClick = { controller.clearWiegandDiagnostics() },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
            }

            OutlinedTextField(
                value = outputText,
                onValueChange = { outputText = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = connected,
                label = { Text("Wiegand OUT HEX 1~16 bytes") },
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useParity, onCheckedChange = { useParity = it })
                Text(if (useParity) "Parity 출력" else "Parity 미출력")
            }
            Button(
                onClick = { statusText = controller.sendWiegandOutput(useParity, outputText) },
                modifier = Modifier.fillMaxWidth(),
                enabled = connected
            ) { Text("SEND") }

            if (controller.wiegandTxLogs.isNotEmpty()) {
                controller.wiegandTxLogs.take(4).forEach { log ->
                    Text(
                        "TX ${log.dataHex} · ${log.parity}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SerialDiagnosticsCard(controller: AccessLinkAppController, port: SerialPort) {
    val connected = controller.serialState.connected
    val entries = when (port) {
        SerialPort.RS232 -> controller.rs232Logs
        SerialPort.RS485 -> controller.rs485Logs
    }
    var mode by remember { mutableStateOf(SerialInputMode.ASCII) }
    var inputText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("대기") }

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(port.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(statusText, if (statusText.contains("실패")) FailRed else ActiveBlue)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { mode = SerialInputMode.ASCII },
                    modifier = Modifier.weight(1f),
                    enabled = mode != SerialInputMode.ASCII
                ) { Text("ASCII") }
                Button(
                    onClick = { mode = SerialInputMode.HEX },
                    modifier = Modifier.weight(1f),
                    enabled = mode != SerialInputMode.HEX
                ) { Text("HEX") }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = connected,
                label = { Text(if (mode == SerialInputMode.ASCII) "ASCII 입력" else "HEX 입력") },
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { statusText = controller.sendSerial(port, mode, inputText) },
                    modifier = Modifier.weight(1f),
                    enabled = connected
                ) { Text("SEND") }
                OutlinedButton(
                    onClick = { inputText = ""; statusText = "대기"; controller.clearSerial(port) },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
            }
            if (entries.isNotEmpty()) {
                entries.take(6).forEach { entry ->
                    Text(
                        "${entry.direction} ${entry.hex} · ${entry.ascii}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun UsbDeviceListCard(
    controller: AccessLinkAppController,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "USB 장치 (${controller.usbDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (controller.usbDevices.isEmpty()) {
                Text(
                    "감지된 USB 장치가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                controller.usbDevices.forEach { device ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(device.displayName, fontWeight = FontWeight.Bold)
                                StatusChip(
                                    text = if (device.hasPermission) "권한 있음" else "권한 필요",
                                    color = if (device.hasPermission) PassGreen else FailRed
                                )
                            }
                            Text(
                                "VID ${device.vendorIdHex} · PID ${device.productIdHex} · ${device.typeGuess}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!device.hasPermission) {
                                Button(
                                    onClick = { onRequestPermission(device) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("USB 권한 요청") }
                            }
                        }
                    }
                }
            }
        }
    }
}
