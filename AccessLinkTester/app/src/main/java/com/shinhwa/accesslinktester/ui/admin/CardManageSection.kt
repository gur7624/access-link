package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.shinhwa.accesslinktester.AccessLinkAppController
import com.shinhwa.accesslinktester.model.CapturedCard
import com.shinhwa.accesslinktester.ui.components.InfoCard
import com.shinhwa.accesslinktester.ui.components.SectionLabel

/**
 * 카드 관리 — 신규 등록(리더기 태그 대기 → 이름 입력 → 저장)과 등록 목록 관리.
 */
@Composable
fun CardManageSection(controller: AccessLinkAppController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("CARD · 카드 관리")

        RegistrationCard(
            active = controller.registrationActive,
            captured = controller.capturedCard,
            onStart = { controller.startCardRegistration() },
            onCancel = { controller.cancelCardRegistration() },
            onConfirm = { controller.confirmCardRegistration(it) }
        )

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "등록 목록 (${controller.cards.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (controller.cards.isEmpty()) {
                    Text(
                        "등록된 카드가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    controller.cards.forEach { card ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(card.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "카드 ${card.key}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { controller.removeCard(card.key) }) {
                                    Text("삭제")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationCard(
    active: Boolean,
    captured: CapturedCard?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("신규 등록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            when {
                !active -> {
                    Text(
                        "카드를 등록하려면 대기 모드를 시작하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("+ 신규 등록")
                    }
                }

                captured == null -> {
                    Text(
                        "카드를 리더기에 대주세요…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("취소")
                    }
                }

                else -> {
                    CapturedCardForm(captured = captured, onCancel = onCancel, onConfirm = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun CapturedCardForm(
    captured: CapturedCard,
    onCancel: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var name by remember(captured.key) { mutableStateOf("") }
    var error by remember(captured.key) { mutableStateOf(false) }

    Text(
        "카드번호 ${captured.cardNumber}",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it; error = false },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error,
        label = { Text("카드 소유자 이름") }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("취소")
        }
        Button(
            onClick = { if (!onConfirm(name)) error = true },
            modifier = Modifier.weight(1f)
        ) {
            Text("저장")
        }
    }
}
