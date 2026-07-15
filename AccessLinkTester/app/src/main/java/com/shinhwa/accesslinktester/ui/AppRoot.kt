package com.shinhwa.accesslinktester.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinhwa.accesslinktester.AccessLinkAppController
import com.shinhwa.accesslinktester.model.UsbDeviceSnapshot
import com.shinhwa.accesslinktester.ui.admin.AdminGate
import com.shinhwa.accesslinktester.ui.admin.AdminScreen
import com.shinhwa.accesslinktester.ui.screens.HomeScreen

private enum class AdminRoute { NONE, GATE, ADMIN }

@Composable
fun AppRoot(
    controller: AccessLinkAppController,
    onConnectSerial: (Int) -> Unit,
    onDisconnectSerial: () -> Unit,
    onRequestPermission: (UsbDeviceSnapshot) -> Unit,
    onRefreshDevices: () -> Unit
) {
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
        topBar = {}
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HomeScreen(
                doors = controller.doors,
                connected = controller.serialState.connected,
                registeredFaceCount = controller.faces.size,
                recentEvents = controller.accessEvents,
                onOpenDoor = { controller.openDoor(it) },
                onFaceEmbedding = { controller.evaluateFaceEmbedding(it) }
            )
            OutlinedButton(
                onClick = { adminRoute = AdminRoute.GATE },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 18.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("관리자", fontSize = 12.sp)
            }
        }
    }
}
