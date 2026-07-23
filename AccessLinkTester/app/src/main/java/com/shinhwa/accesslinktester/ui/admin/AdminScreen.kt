package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.shinhwa.accesslinktester.ui.screens.AccessLogScreen
import com.shinhwa.accesslinktester.ui.theme.AccessOrange
import com.shinhwa.accesslinktester.ui.theme.ActiveBlue
import com.shinhwa.accesslinktester.ui.theme.FailRed
import com.shinhwa.accesslinktester.ui.theme.PassGreen
import com.shinhwa.accesslinktester.ui.theme.ShinhwaBlue
import com.shinhwa.accesslinktester.ui.theme.WaitGray

private enum class AdminRoute(val title: String) {
    DASHBOARD("관리자"),
    SETUP("설치 순서"),
    DOORS("출력 설정 (Relay)"),
    INPUTS("입력 설정 (Input)"),
    CARDS("카드 등록"),
    FACES("얼굴 등록"),
    AUTO("인증 동작 설정"),
    DEVICE("연결·장비 진단"),
    SYSTEM("기록·초기화")
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
    var routeStack by remember { mutableStateOf(listOf(AdminRoute.DASHBOARD)) }
    val route = routeStack.last()
    val navigate: (AdminRoute) -> Unit = { nextRoute ->
        if (nextRoute != route) routeStack = routeStack + nextRoute
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AdminTopBar(
                title = route.title,
                canGoBack = routeStack.size > 1,
                onBack = { routeStack = routeStack.dropLast(1) },
                onExit = onExit
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (route) {
                AdminRoute.DASHBOARD -> AdminDashboard(
                    controller = controller,
                    onNavigate = navigate
                )

                AdminRoute.SETUP -> SetupGuideSection(
                    controller = controller,
                    onNavigate = navigate
                )

                AdminRoute.DOORS -> DoorConfigSection(
                    doors = controller.doors,
                    onUpdateDoor = controller::updateDoor
                )

                AdminRoute.INPUTS -> InputConfigSection(
                    controller = controller,
                    onOpenDiagnostics = { navigate(AdminRoute.DEVICE) }
                )

                AdminRoute.CARDS -> CardManageSection(controller = controller)

                AdminRoute.FACES -> FaceManageSection(controller = controller)

                AdminRoute.AUTO -> AutoOpenSection(
                    settings = controller.settings,
                    doors = controller.doors,
                    onUpdateSettings = controller::updateSettings
                )

                AdminRoute.DEVICE -> DeviceSection(
                    controller = controller,
                    onConnectSerial = onConnectSerial,
                    onDisconnectSerial = onDisconnectSerial,
                    onRequestPermission = onRequestPermission,
                    onRefreshDevices = onRefreshDevices
                )

                AdminRoute.SYSTEM -> SystemSection(controller = controller)
            }
        }
    }
}

@Composable
private fun AdminTopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onExit: () -> Unit
) {
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
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "설정·등록은 이 화면에서만",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canGoBack) {
                        TextButton(onClick = onBack) {
                            Text("뒤로")
                        }
                    }
                    TextButton(onClick = onExit) {
                        Text("나가기")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminDashboard(
    controller: AccessLinkAppController,
    onNavigate: (AdminRoute) -> Unit
) {
    val configuredRelays = controller.doors.count { it.visibleToUser() }
    val connected = controller.serialState.connected
    val hasCredential = controller.cards.isNotEmpty() || controller.faces.isNotEmpty()
    val hasRelayTarget = configuredRelays == 1 || controller.settings.autoOpenDoorIndexes.isNotEmpty()
    val faceReady = controller.faces.isNotEmpty() && hasRelayTarget
    val cardReady = controller.cards.isNotEmpty() && controller.settings.autoOpenEnabled && hasRelayTarget
    val automationReady = faceReady || cardReady

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("관리자 홈")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ACCESS LINK 상태", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (connected) "장비와 통신 연결됨" else "장비 연결 또는 권한 확인 필요",
                            color = if (connected) PassGreen else FailRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Relay ${configuredRelays}/2 사용",
                        color = if (configuredRelays > 0) ShinhwaBlue else WaitGray,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "카드 ${controller.cards.size}장 · 얼굴 ${controller.faces.size}명 · 최근 기록 ${controller.accessEvents.size}건",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionLabel("장비 기본 설정")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AdminMenuButton(
                "장비 연결",
                if (connected) "USB-C 연결됨" else "USB-C·Ethernet 연결 확인",
                { onNavigate(AdminRoute.DEVICE) },
                Modifier.weight(1f)
            )
            AdminMenuButton(
                "출력 설정 (Relay)",
                "Relay 0/1 이름·시간",
                { onNavigate(AdminRoute.DOORS) },
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AdminMenuButton(
                "입력 설정 (Input)",
                "Input 0/1 감지 확인",
                { onNavigate(AdminRoute.INPUTS) },
                Modifier.weight(1f)
            )
            AdminMenuButton(
                "통신 진단",
                "Wiegand·RS-232·RS-485",
                { onNavigate(AdminRoute.DEVICE) },
                Modifier.weight(1f)
            )
        }

        SectionLabel("인증과 동작")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AdminMenuButton("카드 등록", "Wiegand 카드 저장", { onNavigate(AdminRoute.CARDS) }, Modifier.weight(1f))
            AdminMenuButton("얼굴 등록", "카메라 얼굴 저장", { onNavigate(AdminRoute.FACES) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AdminMenuButton(
                "인증 동작 설정",
                if (automationReady) "실행 Relay 설정됨" else "인증 후 실행 Relay 선택",
                { onNavigate(AdminRoute.AUTO) },
                Modifier.weight(1f)
            )
            AdminMenuButton("기록", "인증·출력·통신 이력", { onNavigate(AdminRoute.SYSTEM) }, Modifier.weight(1f))
        }

        SectionLabel("설치 보조")
        Button(
            onClick = { onNavigate(AdminRoute.SETUP) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ShinhwaBlue)
        ) {
            Text("처음 설치 순서 보기")
        }
    }
}

@Composable
private fun SetupGuideSection(
    controller: AccessLinkAppController,
    onNavigate: (AdminRoute) -> Unit
) {
    val connected = controller.serialState.connected
    val configuredDoors = controller.doors.count { it.visibleToUser() }
    val hasCredential = controller.cards.isNotEmpty() || controller.faces.isNotEmpty()
    val hasAutoTarget = controller.settings.autoOpenDoorIndexes.isNotEmpty()
    val stepDone = listOf(
        connected,
        configuredDoors > 0,
        hasCredential,
        configuredDoors == 1 || hasAutoTarget
    )
    val completedSteps = stepDone.count { it }
    val currentStep = when {
        connected.not() -> 1
        configuredDoors == 0 -> 2
        !hasCredential -> 3
        configuredDoors > 1 && !hasAutoTarget -> 4
        else -> 0
    }
    val nextStepTitle = when (currentStep) {
        1 -> "장비 연결"
        2 -> "릴레이 설정"
        3 -> "사용자 등록"
        4 -> "인증 후 실행 Relay 선택"
        else -> "기본 설치 완료"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("처음 설치하는 순서")
        InfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("설치 마법사", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (currentStep == 0) "필수 설정이 모두 끝났습니다."
                        else "다음 할 일: $nextStepTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("$completedSteps/4", color = ShinhwaBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        SetupStepCard(
            number = "1",
            title = "장비 연결 확인",
            description = "USB 권한을 허용하고 통신 속도를 선택합니다.",
            done = connected,
            current = currentStep == 1,
            action = "장비 진단",
            onClick = { onNavigate(AdminRoute.DEVICE) }
        )
        SetupStepCard(
            number = "2",
            title = "릴레이 설정",
            description = "Relay 0·1의 표시 이름, 사용 여부, 출력 시간을 정합니다.",
            done = configuredDoors > 0,
            current = currentStep == 2,
            action = "릴레이 설정",
            onClick = { onNavigate(AdminRoute.DOORS) }
        )
        SetupStepCard(
            number = "3",
            title = "사용자 등록",
            description = "카드 또는 얼굴을 등록합니다. 둘 다 등록할 수도 있습니다.",
            done = hasCredential,
            current = currentStep == 3,
            action = "카드 등록",
            onClick = { onNavigate(AdminRoute.CARDS) },
            secondaryAction = "얼굴 등록",
            onSecondaryClick = { onNavigate(AdminRoute.FACES) }
        )
        SetupStepCard(
            number = "4",
            title = "인증 후 실행 Relay 선택",
            description = "Relay가 여러 개일 때 인증 성공 후 실행할 출력을 지정합니다.",
            done = configuredDoors == 1 || hasAutoTarget,
            current = currentStep == 4,
            action = "인증 동작",
            onClick = { onNavigate(AdminRoute.AUTO) }
        )
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SetupStatusRow(title: String, status: String, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (done) PassGreen else WaitGray,
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                if (done) "완료" else "대기",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun AdminMenuButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 76.dp)) {
        Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupStepCard(
    number: String,
    title: String,
    description: String,
    done: Boolean,
    current: Boolean = false,
    action: String,
    onClick: () -> Unit,
    secondaryAction: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (done) PassGreen else if (current) ShinhwaBlue else WaitGray,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        number,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (done) "완료" else if (current) "지금 할 일" else "대기", fontSize = 12.sp, color = if (done) PassGreen else if (current) ShinhwaBlue else FailRed)
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (secondaryAction != null && onSecondaryClick != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(action) }
                    OutlinedButton(onClick = onSecondaryClick, modifier = Modifier.weight(1f)) { Text(secondaryAction) }
                }
            } else {
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 인증 동작 — 등록 카드 또는 얼굴 인증 성공 시 실행할 Relay 선택
// ---------------------------------------------------------------------------

@Composable
private fun AutoOpenSection(
    settings: ServiceSettings,
    doors: List<DoorConfig>,
    onUpdateSettings: (ServiceSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("인증 동작 설정")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "등록 카드 인증 동작",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "켜면 등록 카드 인증 성공 시 선택한 Relay에 출력 명령을 전송합니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoOpenEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoOpenEnabled = it)) }
                    )
                }

                Text(
                    "얼굴 인증은 등록 얼굴이 일치하면 선택한 Relay에 출력 명령을 전송합니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("인증 후 실행할 Relay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val selectable = doors.filter { it.visibleToUser() }
                if (selectable.isEmpty()) {
                    Text(
                        "먼저 릴레이 설정에서 표시 이름과 사용 여부를 지정하세요.",
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
                                enabled = true,
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
        SectionLabel("기록·초기화")

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

        AccessLogScreen(events = controller.accessEvents)

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("데이터 초기화", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "릴레이 설정·카드·인증 동작 설정·출입 기록을 모두 삭제합니다.",
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
