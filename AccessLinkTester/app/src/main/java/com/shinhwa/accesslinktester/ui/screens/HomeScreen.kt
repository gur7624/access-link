package com.shinhwa.accesslinktester.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinhwa.accesslinktester.FaceAuthDecision
import com.shinhwa.accesslinktester.model.AccessEvent
import com.shinhwa.accesslinktester.model.AccessEventType
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.model.visibleToUser
import com.shinhwa.accesslinktester.ui.face.FaceCameraView
import com.shinhwa.accesslinktester.ui.face.FaceDetectionState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val CyberNavy = Color(0xFF061426)
private val CyberPanel = Color(0xFF0E243C)
private val CyberPanelElevated = Color(0xFF142B46)
private val CyberLine = Color(0xFF72D7FF)
private val CyberBlue = Color(0xFF1F8BFF)
private val CyberTextMuted = Color(0xFF91A8C2)
private val CyberWhite = Color(0xFFF4F8FC)
private val CyberDanger = Color(0xFFFF6B6B)
private val CyberSuccess = Color(0xFF3DEBA4)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

private val RECENT_TYPES = setOf(
    AccessEventType.OPEN,
    AccessEventType.GRANTED,
    AccessEventType.DENIED,
    AccessEventType.TAG,
    AccessEventType.FACE
)

/**
 * 사용자 첫 화면.
 * 부장님 시안처럼 안면인식/카드 리더기 대기 영역을 크게 두고, 관리자 진입은 작게 분리한다.
 */
@Composable
fun HomeScreen(
    doors: List<DoorConfig>,
    connected: Boolean,
    registeredFaceCount: Int,
    recentEvents: List<AccessEvent>,
    onOpenDoor: (Int) -> Unit,
    onFaceEmbedding: (FloatArray) -> FaceAuthDecision,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleDoors = doors.filter { it.visibleToUser() }
    val recent = recentEvents.firstOrNull { it.type in RECENT_TYPES }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF050A12), CyberNavy, Color(0xFF091A2C)),
                    startY = 0f,
                    endY = 1400f
                )
            )
            .drawCyberGrid()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(connected = connected, onAdminClick = onAdminClick)

            RecognitionPanel(
                connected = connected,
                registeredFaceCount = registeredFaceCount,
                onFaceEmbedding = onFaceEmbedding
            )

            CardReaderPanel(
                connected = connected,
                recent = recent,
                visibleDoors = visibleDoors,
                onOpenDoor = onOpenDoor
            )
        }
    }
}

@Composable
private fun Header(connected: Boolean, onAdminClick: () -> Unit) {
    var currentTime by remember { mutableStateOf(LocalTime.now().format(TIME_FORMATTER)) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now().format(TIME_FORMATTER)
            delay(30_000)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("신화시스템", color = CyberWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("ACCESS LINK", color = CyberLine, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("스마트 로비", color = CyberTextMuted, fontSize = 12.sp)
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(currentTime, color = CyberWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (connected) CyberSuccess else CyberDanger, RoundedCornerShape(4.dp))
                )
                Text(
                    if (connected) "장비 연결됨" else "장비 미연결",
                    color = CyberWhite.copy(alpha = 0.82f),
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onAdminClick,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5D55E8),
                    contentColor = Color.White
                )
            ) {
                Text("관리자", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecognitionPanel(
    connected: Boolean,
    registeredFaceCount: Int,
    onFaceEmbedding: (FloatArray) -> FaceAuthDecision
) {
    var faceState by remember { mutableStateOf(FaceDetectionState()) }
    var authDecision by remember { mutableStateOf<FaceAuthDecision?>(null) }
    val statusText = when {
        faceState.faceCount == 0 -> "얼굴을 찾는 중입니다"
        faceState.faceCount > 1 -> "한 명씩 인증해 주세요"
        registeredFaceCount == 0 -> "등록된 얼굴이 없습니다"
        faceState.embedding == null -> "얼굴 특징값 생성 중"
        authDecision is FaceAuthDecision.Granted -> authDecision?.message ?: "인증되었습니다"
        authDecision is FaceAuthDecision.Denied -> "등록되지 않은 사용자입니다"
        authDecision is FaceAuthDecision.Blocked -> authDecision?.message ?: "인증 대기"
        else -> "안면 인증 대기"
    }
    val statusColor = when {
        authDecision is FaceAuthDecision.Granted -> CyberSuccess
        authDecision is FaceAuthDecision.Denied -> CyberDanger
        registeredFaceCount == 0 && faceState.faceCount == 1 -> CyberDanger
        faceState.faceCount == 1 -> if (connected) CyberLine else Color(0xFFFFD166)
        else -> CyberTextMuted
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberLine.copy(alpha = 0.72f), RoundedCornerShape(12.dp)),
        color = CyberPanel.copy(alpha = 0.88f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 540.dp)
                    .aspectRatio(0.84f)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, CyberLine.copy(alpha = 0.52f), RoundedCornerShape(9.dp))
            ) {
                FaceCameraView(
                    modifier = Modifier.fillMaxSize(),
                    enableRecognition = registeredFaceCount > 0,
                    onFaceState = { state ->
                        faceState = state
                        if (state.faceCount != 1) {
                            authDecision = null
                        }
                        state.embedding?.let { embedding ->
                            authDecision = onFaceEmbedding(embedding)
                        }
                    }
                )
                FaceGuideOverlay(modifier = Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    color = CyberNavy.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("얼굴 인증", color = CyberWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("얼굴을 가이드 중앙에 맞춰 주세요", color = CyberTextMuted, fontSize = 11.sp)
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    color = CyberNavy.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(statusText, color = statusColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (authDecision is FaceAuthDecision.Granted) "인증된 얼굴일 때만 문 개방 명령을 전송합니다."
                            else "카메라를 바라봐 주세요",
                            color = CyberWhite.copy(alpha = 0.82f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardReaderPanel(
    connected: Boolean,
    recent: AccessEvent?,
    visibleDoors: List<DoorConfig>,
    onOpenDoor: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberLine.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        color = CyberPanelElevated,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .border(2.dp, CyberLine, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("RFID", color = CyberLine, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("카드로도 출입할 수 있습니다", color = CyberWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (connected) "등록된 카드를 리더기에 태그해 주세요" else "장비 연결 후 카드 인증을 사용할 수 있습니다",
                        color = CyberTextMuted,
                        fontSize = 12.sp
                    )
                }
                StatusPill(status = if (connected) "대기" else "미연결", bright = false)
            }

            RecentLine(recent = recent)

            if (visibleDoors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("문 개방", color = CyberTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    visibleDoors.forEach { door ->
                        Button(
                            onClick = { onOpenDoor(door.index) },
                            enabled = connected,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberBlue,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF263A50),
                                disabledContentColor = CyberTextMuted
                            )
                        ) {
                            Text("${door.name} OPEN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                Text(
                    "관리자 화면에서 문 이름과 사용 여부를 설정하면 수동 개방 버튼이 표시됩니다.",
                    color = CyberTextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RecentLine(recent: AccessEvent?) {
    Surface(
        color = Color(0xFF0A1B30),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(CyberBlue, RoundedCornerShape(4.dp))
            )
            Text(
                recent?.let { "${it.time}  ${it.message}" } ?: "최근 인증 기록 없음",
                color = CyberWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusPill(status: String, bright: Boolean) {
    val background = if (bright) Color(0xFFE5F2FF) else Color.White.copy(alpha = 0.12f)
    val textColor = if (bright) CyberBlue else Color.White
    Surface(color = background, shape = RoundedCornerShape(999.dp)) {
        Text(
            status,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FaceGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val guideWidth = size.width * 0.62f
        val guideHeight = size.height * 0.60f
        val left = (size.width - guideWidth) / 2f
        val top = (size.height - guideHeight) / 2f - size.height * 0.02f
        val guideColor = CyberLine.copy(alpha = 0.62f)
        val strokeWidth = 2.dp.toPx()
        val cornerLength = 24.dp.toPx()

        drawOval(
            color = guideColor.copy(alpha = 0.42f),
            topLeft = Offset(left, top),
            size = Size(guideWidth, guideHeight),
            style = Stroke(width = strokeWidth)
        )

        drawLine(guideColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, StrokeCap.Square)
        drawLine(guideColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth, StrokeCap.Square)
        drawLine(guideColor, Offset(left + guideWidth, top), Offset(left + guideWidth - cornerLength, top), strokeWidth, StrokeCap.Square)
        drawLine(guideColor, Offset(left + guideWidth, top), Offset(left + guideWidth, top + cornerLength), strokeWidth, StrokeCap.Square)
        drawLine(guideColor, Offset(left, top + guideHeight), Offset(left + cornerLength, top + guideHeight), strokeWidth, StrokeCap.Square)
        drawLine(guideColor, Offset(left, top + guideHeight), Offset(left, top + guideHeight - cornerLength), strokeWidth, StrokeCap.Square)
        drawLine(guideColor, Offset(left + guideWidth, top + guideHeight), Offset(left + guideWidth - cornerLength, top + guideHeight), strokeWidth, StrokeCap.Square)
        drawLine(guideColor, Offset(left + guideWidth, top + guideHeight), Offset(left + guideWidth, top + guideHeight - cornerLength), strokeWidth, StrokeCap.Square)
    }
}

private fun Modifier.drawCyberGrid(): Modifier = drawBehind {
    val lineColor = Color.White.copy(alpha = 0.055f)
    val step = 42.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}
