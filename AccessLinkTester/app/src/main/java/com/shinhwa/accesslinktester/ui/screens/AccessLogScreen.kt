package com.shinhwa.accesslinktester.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinhwa.accesslinktester.model.AccessEvent
import com.shinhwa.accesslinktester.model.AccessEventType
import com.shinhwa.accesslinktester.ui.components.SectionLabel
import com.shinhwa.accesslinktester.ui.theme.AccessOrange
import com.shinhwa.accesslinktester.ui.theme.ActiveBlue
import com.shinhwa.accesslinktester.ui.theme.FailRed
import com.shinhwa.accesslinktester.ui.theme.PassGreen
import com.shinhwa.accesslinktester.ui.theme.WaitGray

// 출입 기록 필터 — 전체/개방/인증/거부
private enum class LogFilter(val label: String, val types: Set<AccessEventType>?) {
    ALL("전체", null),
    OPEN("개방", setOf(AccessEventType.OPEN)),
    GRANTED("인증", setOf(AccessEventType.GRANTED, AccessEventType.TAG, AccessEventType.FACE)),
    DENIED("거부", setOf(AccessEventType.DENIED))
}

@Composable
fun AccessLogScreen(
    events: List<AccessEvent>,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    val visible = events.filter { filter.types == null || it.type in filter.types!! }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionLabel("ACCESS LOG · 출입 기록")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) }
                )
            }
        }

        if (visible.isEmpty()) {
            Text(
                "기록 없음",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { event ->
                    AccessEventRow(event)
                }
            }
        }
    }
}

/** "누가 · 언제 · 카드번호" 한 줄. 등록자면 이름, 미등록이면 "미등록 카드". */
@Composable
fun AccessEventRow(event: AccessEvent) {
    val tone = event.type.tone()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = event.personName ?: (event.cardNumber?.let { "미등록 카드" } ?: event.message),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                event.cardNumber?.let { number ->
                    Text(
                        text = "카드 $number",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(color = tone, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = event.type.badge(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = event.time,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun AccessEventType.tone(): Color = when (this) {
    AccessEventType.OPEN -> AccessOrange
    AccessEventType.GRANTED -> PassGreen
    AccessEventType.DENIED, AccessEventType.ERROR -> FailRed
    AccessEventType.TAG, AccessEventType.FACE, AccessEventType.INPUT -> ActiveBlue
    AccessEventType.SYSTEM -> WaitGray
}

private fun AccessEventType.badge(): String = when (this) {
    AccessEventType.OPEN -> "개방"
    AccessEventType.GRANTED -> "인증"
    AccessEventType.DENIED -> "거부"
    AccessEventType.TAG -> "태그"
    AccessEventType.FACE -> "안면"
    AccessEventType.INPUT -> "입력"
    AccessEventType.SYSTEM -> "시스템"
    AccessEventType.ERROR -> "오류"
}
