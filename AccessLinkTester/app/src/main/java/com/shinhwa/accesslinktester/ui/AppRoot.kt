package com.shinhwa.accesslinktester.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinhwa.accesslinktester.AccessLinkAppController
import com.shinhwa.accesslinktester.model.UsbDeviceSnapshot
import com.shinhwa.accesslinktester.ui.admin.AdminGate
import com.shinhwa.accesslinktester.ui.admin.AdminScreen
import com.shinhwa.accesslinktester.ui.screens.AccessLogScreen
import com.shinhwa.accesslinktester.ui.screens.HomeScreen
import com.shinhwa.accesslinktester.ui.theme.AccessOrange
import com.shinhwa.accesslinktester.ui.theme.ShinhwaBlue

private enum class UserTab(val label: String) {
    HOME("홈"),
    LOG("출입 기록")
}

private enum class AdminRoute { NONE, GATE, ADMIN }

@Composable
fun AppRoot(
    controller: AccessLinkAppController,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit,
    onRefreshDevices: () -> Unit
) {
    var tab by remember { mutableStateOf(UserTab.HOME) }
    var adminRoute by remember { mutableStateOf(AdminRoute.NONE) }

    // 관리자 화면 (PIN 게이트 → 관리자 본문)
    if (adminRoute != AdminRoute.NONE) {
        when (adminRoute) {
            AdminRoute.GATE -> AdminGate(
                onSubmit = { pin ->
                    val ok = controller.tryAdminLogin(pin)
                    if (ok) adminRoute = AdminRoute.ADMIN
                    ok
                },
                onCancel = { adminRoute = AdminRoute.NONE }
            )

            AdminRoute.ADMIN -> AdminScreen(
                controller = controller,
                onConnectSerial = onConnectSerial,
                onDisconnectSerial = onDisconnectSerial,
                onRequestPermission = onRequestPermission,
                onRefreshDevices = onRefreshDevices,
                onExit = {
                    controller.leaveAdmin()  // 세션 종료 — 재진입 시 PIN 재요구
                    adminRoute = AdminRoute.NONE
                }
            )

            AdminRoute.NONE -> Unit
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            UserTopBar(
                connected = controller.serialState.connected,
                onAdminClick = { adminRoute = AdminRoute.GATE }
            )
        },
        bottomBar = {
            UserBottomBar(selected = tab, onSelect = { tab = it })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                UserTab.HOME -> HomeScreen(
                    doors = controller.doors,
                    connected = controller.serialState.connected,
                    recentEvents = controller.accessEvents,
                    onOpenDoor = { controller.openDoor(it) }
                )

                UserTab.LOG -> AccessLogScreen(events = controller.accessEvents)
            }
        }
    }
}

@Composable
private fun UserTopBar(
    connected: Boolean,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            Box(modifier = Modifier.weight(3f).height(4.dp).background(ShinhwaBlue))
            Box(modifier = Modifier.weight(1f).height(4.dp).background(AccessOrange))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(AccessOrange, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column {
                    Text(
                        "SHINHWA SYSTEM",
                        color = ShinhwaBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "ACCESS LINK",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionDot(connected = connected)
                OutlinedButton(onClick = onAdminClick) {
                    Text("🔒 관리자")
                }
            }
        }
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (connected) com.shinhwa.accesslinktester.ui.theme.PassGreen
                    else com.shinhwa.accesslinktester.ui.theme.WaitGray,
                    RoundedCornerShape(4.dp)
                )
        )
        Text(
            if (connected) "연결됨" else "미연결",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UserBottomBar(
    selected: UserTab,
    onSelect: (UserTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            UserTab.entries.forEach { entry ->
                val isSelected = entry == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(entry) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 3.dp)
                            .background(
                                if (isSelected) AccessOrange else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Text(
                        text = entry.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AccessOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
