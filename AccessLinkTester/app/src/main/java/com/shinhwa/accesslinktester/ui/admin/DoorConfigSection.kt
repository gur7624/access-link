package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.ui.components.InfoCard
import com.shinhwa.accesslinktester.ui.components.SectionLabel

/**
 * 릴레이 설정 — Relay 0/1 각각 표시 이름·출력 시간(초, 0=지속)·사용 여부.
 * 표시 이름이 비었거나 사용 off면 사용자 홈에 노출되지 않는다.
 */
@Composable
fun DoorConfigSection(
    doors: List<DoorConfig>,
    onUpdateDoor: (DoorConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("릴레이 설정")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Relay 0/1 출력 설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "ACCESS LINK는 Relay 0/1 두 출력을 제공합니다. 현장 용도에 맞춰 표시 이름, 사용 여부, 출력 시간을 지정하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        doors.forEach { door ->
            DoorConfigCard(door = door, onUpdateDoor = onUpdateDoor)
        }
    }
}

@Composable
private fun DoorConfigCard(
    door: DoorConfig,
    onUpdateDoor: (DoorConfig) -> Unit
) {
    var name by remember(door.index) { mutableStateOf(door.name) }
    var seconds by remember(door.index) { mutableStateOf(door.openSeconds.toString()) }
    var enabled by remember(door.index) { mutableStateOf(door.enabled) }

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Relay ${door.index}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("사용", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("표시 이름 (예: 정문, 조명, 락커)") }
            )

            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it.filter { c -> c.isDigit() }.take(2) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("출력 시간 (초, 0=지속)") }
            )

            Button(
                onClick = {
                    onUpdateDoor(
                        door.copy(
                            name = name.trim(),
                            openSeconds = seconds.toIntOrNull()?.coerceIn(0, 99) ?: door.openSeconds,
                            enabled = enabled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("저장")
            }
        }
    }
}
