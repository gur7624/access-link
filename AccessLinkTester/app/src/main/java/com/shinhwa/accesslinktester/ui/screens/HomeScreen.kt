package com.shinhwa.accesslinktester.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val CyberNavy = Color(0xFF061426)
private val CyberPanel = Color(0xFF0E243C)
private val CyberPanelLight = Color(0xFFF8FBFF)
private val CyberLine = Color(0xFF72D7FF)
private val CyberBlue = Color(0xFF1F8BFF)
private val CyberTextMuted = Color(0xFF91A8C2)

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
    modifier: Modifier = Modifier
) {
    val visibleDoors = doors.filter { it.visibleToUser() }
    val recent = recentEvents.firstOrNull { it.type in RECENT_TYPES }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CyberNavy, Color(0xFF0A1C32), Color(0xFFF7FAFF)),
                    startY = 0f,
                    endY = 1600f
                )
            )
            .drawCyberGrid()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 54.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(connected = connected)

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
private fun Header(connected: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "SHINHWA ACCESS LINK",
            color = Color.White.copy(alpha = 0.74f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "출입 인증 대기",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(if (connected) Color(0xFF3DEBA4) else Color(0xFFFFD166), RoundedCornerShape(5.dp))
            )
            Text(
                if (connected) "장비 연결됨" else "장비 연결 대기",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp
            )
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberLine.copy(alpha = 0.56f), RoundedCornerShape(10.dp)),
        color = CyberPanel.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("안면인식", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("전면 카메라 얼굴 검출", color = CyberTextMuted, fontSize = 13.sp)
                }
                StatusPill(status = if (connected) "대기" else "미연결", bright = false)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .border(1.dp, CyberLine.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
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
                CornerMark(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                CornerMark(modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
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
                authDecision is FaceAuthDecision.Granted -> Color(0xFF3DEBA4)
                authDecision is FaceAuthDecision.Denied -> Color(0xFFFF6B6B)
                registeredFaceCount == 0 && faceState.faceCount == 1 -> Color(0xFFFF6B6B)
                faceState.faceCount == 1 -> if (connected) CyberLine else Color(0xFFFFD166)
                else -> CyberTextMuted
            }
            Text(statusText, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "등록된 얼굴과 일치할 때만 문 개방 명령을 전송합니다.",
                color = CyberTextMuted,
                fontSize = 12.sp
            )
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
            .border(1.dp, CyberBlue.copy(alpha = 0.42f), RoundedCornerShape(10.dp)),
        color = CyberPanelLight,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("카드 리더기", color = CyberNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (connected) "카드를 태그해 주세요" else "장비 연결 후 사용 가능",
                        color = Color(0xFF52677F),
                        fontSize = 13.sp
                    )
                }
                StatusPill(status = if (connected) "대기" else "미연결", bright = true)
            }

            RecentLine(recent = recent)

            if (visibleDoors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("수동 개방", color = Color(0xFF52677F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    visibleDoors.forEach { door ->
                        Button(
                            onClick = { onOpenDoor(door.index) },
                            enabled = connected,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberBlue,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFDDE6F1),
                                disabledContentColor = Color(0xFF7B8EA5)
                            )
                        ) {
                            Text("${door.name} 열기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                Text(
                    "관리자 화면에서 문 이름과 사용 여부를 설정하면 수동 개방 버튼이 표시됩니다.",
                    color = Color(0xFF52677F),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RecentLine(recent: AccessEvent?) {
    Surface(
        color = Color(0xFFEAF3FF),
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
                color = CyberNavy,
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
private fun CornerMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .border(2.dp, CyberLine.copy(alpha = 0.78f), RoundedCornerShape(2.dp))
    )
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
