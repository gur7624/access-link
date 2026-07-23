package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shinhwa.accesslinktester.AccessLinkAppController
import com.shinhwa.accesslinktester.ui.components.DetailGrid
import com.shinhwa.accesslinktester.ui.components.InfoCard
import com.shinhwa.accesslinktester.ui.components.SectionLabel
import com.shinhwa.accesslinktester.ui.components.StatusChip
import com.shinhwa.accesslinktester.ui.theme.ActiveBlue
import com.shinhwa.accesslinktester.ui.theme.FailRed
import com.shinhwa.accesslinktester.ui.theme.PassGreen
import com.shinhwa.accesslinktester.ui.theme.WaitGray

/**
 * Digital Input 설정/상태 화면.
 * 현재 단계에서는 입력 상태 수신 확인을 제공하고, 입력-릴레이 자동 연동은 별도 정책 화면에서 확장한다.
 */
@Composable
fun InputConfigSection(
    controller: AccessLinkAppController,
    onOpenDiagnostics: () -> Unit
) {
    val diagnostics = controller.diagnostics

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("입력 설정")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Digital Input 0/1", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "외부 버튼, 센서, 리더기 접점처럼 ACCESS LINK의 Digital Input 단자에 들어오는 신호를 확인합니다. 입력 신호가 릴레이를 자동 실행하는 정책은 다음 단계에서 별도 설정으로 분리합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DetailGrid(
                    items = listOf(
                        "장비 통신" to if (controller.serialState.connected) "USB 연결됨" else "연결 필요",
                        "마지막 원문" to (diagnostics.lastRawHex ?: "없음")
                    )
                )
            }
        }

        InputStatusCard(
            title = "Input 0",
            status = diagnostics.input0,
            description = "DIGITAL INPUT의 IN0 단자 상태"
        )
        InputStatusCard(
            title = "Input 1",
            status = diagnostics.input1,
            description = "DIGITAL INPUT의 IN1 단자 상태"
        )

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("상세 통신 확인", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Wiegand, RS-232, RS-485 원문 수신과 릴레이 송신 결과는 장비 진단 화면에서 확인합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Text("고급 장비 진단 열기")
                }
            }
        }
    }
}

@Composable
private fun InputStatusCard(
    title: String,
    status: String?,
    description: String
) {
    val label = inputStatusLabel(status)
    val color = inputStatusColor(status)

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(label, color)
            }
            Text(
                "수신값: ${status ?: "아직 수신 없음"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun inputStatusLabel(status: String?): String = when (status) {
    "Pressed" -> "감지됨"
    "Released" -> "대기"
    null -> "수신 없음"
    else -> "확인 필요"
}

private fun inputStatusColor(status: String?) = when (status) {
    "Pressed" -> PassGreen
    "Released" -> ActiveBlue
    null -> WaitGray
    else -> FailRed
}
