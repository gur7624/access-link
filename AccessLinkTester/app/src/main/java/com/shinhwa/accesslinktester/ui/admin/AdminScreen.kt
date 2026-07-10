package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinhwa.accesslinktester.AccessLinkAppController
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.model.ServiceSettings
import com.shinhwa.accesslinktester.model.UsbDeviceSnapshot
import com.shinhwa.accesslinktester.model.visibleToUser
import com.shinhwa.accesslinktester.ui.components.InfoCard
import com.shinhwa.accesslinktester.ui.components.SectionLabel
import com.shinhwa.accesslinktester.ui.theme.AccessOrange
import com.shinhwa.accesslinktester.ui.theme.FailRed
import com.shinhwa.accesslinktester.ui.theme.ShinhwaBlue

private enum class AdminTab(val label: String) {
    DOORS("문 설정"),
    CARDS("카드"),
    AUTO("자동 개방"),
    DEVICE("장비"),
    SYSTEM("시스템")
}

@Composable
fun AdminScreen(
    controller: AccessLinkAppController,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit,
    onRefreshDevices: () -> Unit,
    onExit: () -> Unit
) {
    var tab by remember { mutableStateOf(AdminTab.DOORS) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AdminTopBar(onExit = onExit) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScrollableTabRow(
                selectedTabIndex = tab.ordinal,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AccessOrange
            ) {
                AdminTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(entry.label) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (tab) {
                    AdminTab.DOORS -> DoorConfigSection(
                        doors = controller.doors,
                        onUpdateDoor = controller::updateDoor
                    )

                    AdminTab.CARDS -> CardManageSection(controller = controller)

                    AdminTab.AUTO -> AutoOpenSection(
                        settings = controller.settings,
                        doors = controller.doors,
                        onUpdateSettings = controller::updateSettings
                    )

                    AdminTab.DEVICE -> DeviceSection(
                        controller = controller,
                        onConnectSerial = onConnectSerial,
                        onDisconnectSerial = onDisconnectSerial,
                        onRequestPermission = onRequestPermission,
                        onRefreshDevices = onRefreshDevices
                    )

                    AdminTab.SYSTEM -> SystemSection(controller = controller)
                }
            }
        }
    }
}

@Composable
private fun AdminTopBar(onExit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(0.dp)) {
                Box(modifier = Modifier.weight(3f).background(ShinhwaBlue).padding(2.dp))
                Box(modifier = Modifier.weight(1f).background(AccessOrange).padding(2.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "관리자",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "설정·등록은 이 화면에서만",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onExit) {
                    Text("나가기")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 자동 개방 — 등록 카드 인증 시 자동 개방 on/off + 대상 문 선택
// ---------------------------------------------------------------------------

@Composable
private fun AutoOpenSection(
    settings: ServiceSettings,
    doors: List<DoorConfig>,
    onUpdateSettings: (ServiceSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("AUTO OPEN · 자동 개방")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "등록 카드 자동 개방",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "켜면 등록 카드 인증 시 대상 문을 자동으로 엽니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoOpenEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoOpenEnabled = it)) }
                    )
                }

                Text("대상 문", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val selectable = doors.filter { it.visibleToUser() }
                if (selectable.isEmpty()) {
                    Text(
                        "먼저 문 설정에서 이름·사용 여부를 지정하세요.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    selectable.forEach { door ->
                        val selected = door.index in settings.autoOpenDoorIndexes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(door.name, style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = selected,
                                enabled = settings.autoOpenEnabled,
                                onCheckedChange = { checked ->
                                    val next = if (checked) {
                                        settings.autoOpenDoorIndexes + door.index
                                    } else {
                                        settings.autoOpenDoorIndexes - door.index
                                    }
                                    onUpdateSettings(settings.copy(autoOpenDoorIndexes = next))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 시스템 — 통신 원문 로그, 데이터 초기화
// ---------------------------------------------------------------------------

@Composable
private fun SystemSection(controller: AccessLinkAppController) {
    var confirmReset by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("SYSTEM · 시스템")

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("통신 로그", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (controller.systemLogs.isEmpty()) {
                    Text(
                        "로그 없음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    controller.systemLogs.take(40).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("데이터 초기화", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "문 설정·카드·자동개방·출입 기록을 모두 삭제합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!confirmReset) {
                    Button(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FailRed)
                    ) { Text("초기화") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { confirmReset = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("취소") }
                        Button(
                            onClick = { controller.resetAllData(); confirmReset = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = FailRed)
                        ) { Text("정말 초기화") }
                    }
                }
            }
        }
    }
}
