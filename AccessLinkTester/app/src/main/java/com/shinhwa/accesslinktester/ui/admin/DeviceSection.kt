package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("DEVICE · 장비 진단")

        ConnectionCard(controller, onConnectSerial, onDisconnectSerial, onRefreshDevices)
        WiegandDiagnosticsCard(controller)
        SerialDiagnosticsCard(controller, SerialPort.RS232)
        SerialDiagnosticsCard(controller, SerialPort.RS485)
        UsbDeviceListCard(controller, onRequestPermission)
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
                Text("연결", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(
                    text = if (serial.connected) "연결" else "미연결",
                    color = if (serial.connected) PassGreen else WaitGray
                )
            }
            DetailGrid(
                items = listOf(
                    "Baudrate" to serial.baudRate.toString(),
                    "상태" to serial.status,
                    "Ethernet" to controller.ethernetState.detail,
                    "USB 장치" to "${controller.usbDevices.size}개"
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onConnectSerial(9600) }, modifier = Modifier.weight(1f)) { Text("9600") }
                Button(onClick = { onConnectSerial(115200) }, modifier = Modifier.weight(1f)) { Text("115200") }
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
            Text("Wiegand", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
