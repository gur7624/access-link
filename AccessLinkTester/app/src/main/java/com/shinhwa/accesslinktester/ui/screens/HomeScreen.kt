package com.shinhwa.accesslinktester.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shinhwa.accesslinktester.model.AccessEvent
import com.shinhwa.accesslinktester.model.AccessEventType
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.model.visibleToUser
import com.shinhwa.accesslinktester.ui.components.InfoCard
import com.shinhwa.accesslinktester.ui.components.OpenButton
import com.shinhwa.accesslinktester.ui.components.SectionLabel

private val RECENT_TYPES = setOf(
    AccessEventType.OPEN,
    AccessEventType.GRANTED,
    AccessEventType.DENIED,
    AccessEventType.TAG
)

/**
 * 사용자 홈 — 관리자가 이름을 붙인 문마다 큰 OPEN 버튼 하나씩.
 * 잠금/OFF 버튼 없음. 하단에 최근 출입 미리보기.
 */
@Composable
fun HomeScreen(
    doors: List<DoorConfig>,
    connected: Boolean,
    recentEvents: List<AccessEvent>,
    onOpenDoor: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleDoors = doors.filter { it.visibleToUser() }
    val recent = recentEvents.filter { it.type in RECENT_TYPES }.take(5)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel("DOOR · 문 열기")

        if (visibleDoors.isEmpty()) {
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "표시할 문이 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "관리자 화면에서 문 이름과 사용 여부를 설정하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visibleDoors.forEach { door ->
                    OpenButton(
                        label = "${door.name} 열기",
                        enabled = connected,
                        onClick = { onOpenDoor(door.index) }
                    )
                }
                if (!connected) {
                    Text(
                        "장비 연결을 확인하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("RECENT · 최근 출입")
            if (recent.isEmpty()) {
                Text(
                    "최근 출입 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recent.forEach { event ->
                    AccessEventRow(event)
                }
            }
        }
    }
}
