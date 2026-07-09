package com.shinhwa.accesslinktester.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinhwa.accesslinktester.ui.theme.AccessOrange
import com.shinhwa.accesslinktester.ui.theme.AccessOrangeTint
import com.shinhwa.accesslinktester.ui.theme.ActiveBlue
import com.shinhwa.accesslinktester.ui.theme.FailRed
import com.shinhwa.accesslinktester.ui.theme.FailRedTint
import com.shinhwa.accesslinktester.ui.theme.PassGreen
import com.shinhwa.accesslinktester.ui.theme.PassGreenTint
import com.shinhwa.accesslinktester.ui.theme.ShinhwaBlue
import com.shinhwa.accesslinktester.ui.theme.ShinhwaBlueTint
import com.shinhwa.accesslinktester.ui.theme.WaitGray
import com.shinhwa.accesslinktester.ui.theme.WaitGrayTint

// ---------------------------------------------------------------------------
// 상태 톤 — docs/project-notes.md 상태 색상 규칙의 코드 표현.
// 모든 컴포넌트가 이 enum으로 색을 결정하므로 색 하드코딩이 흩어지지 않는다.
// ---------------------------------------------------------------------------

enum class StatusTone {
    PASS,      // 정상 / 수신 / 연결 — 초록
    FAIL,      // 실패 / 오류 — 빨강
    ACTIVE,    // 진행 중 / 활성 / 권한 요청 — 파랑
    WAIT       // 대기 / 미확인 / UNKNOWN — 회색
}

private val StatusTone.solid: Color
    get() = when (this) {
        StatusTone.PASS -> PassGreen
        StatusTone.FAIL -> FailRed
        StatusTone.ACTIVE -> ActiveBlue
        StatusTone.WAIT -> WaitGray
    }

private val StatusTone.tint: Color
    get() = when (this) {
        StatusTone.PASS -> PassGreenTint
        StatusTone.FAIL -> FailRedTint
        StatusTone.ACTIVE -> ShinhwaBlueTint
        StatusTone.WAIT -> WaitGrayTint
    }

// ---------------------------------------------------------------------------
// 로그 라인 — 대시보드 실시간 로그용 경량 모델.
// direction: "TX"(앱→장비, 파랑) / "RX"(장비→앱, 오렌지) / "SYS"(회색)
// ---------------------------------------------------------------------------

data class DashboardLogLine(
    val direction: String,
    val text: String
)

// ---------------------------------------------------------------------------
// 하단 탭
// ---------------------------------------------------------------------------

enum class DashboardTab(val label: String) {
    DASHBOARD("대시보드"),
    SERIAL("시리얼"),
    WIEGAND("Wiegand"),
    LOG("로그")
}

// ---------------------------------------------------------------------------
// 상단 브랜드 헤더
// 마케팅 자료 표지의 파랑+오렌지 브랜드 바를 4dp 스트립으로 반영.
// ---------------------------------------------------------------------------

@Composable
fun BrandTopBar(
    connectionLabel: String,
    connectionTone: StatusTone,
    chips: List<Pair<String, Boolean>>,
    onRefresh: () -> Unit,
    onAdminToggle: () -> Unit,
    adminMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            Box(
                modifier = Modifier
                    .weight(3f)
                    .height(4.dp)
                    .background(ShinhwaBlue)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(AccessOrange)
            )
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            Text(
                                text = "AL",
                                color = AccessOrangeTint,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Column {
                            Text(
                                text = "SHINHWA SYSTEM",
                                color = ShinhwaBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "ACCESS LINK · SHAL-1000",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    ConnectionPill(label = connectionLabel, tone = connectionTone)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { (label, active) ->
                        InfoChip(label = label, active = active)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                        Text("새로고침")
                    }
                    OutlinedButton(onClick = onAdminToggle, modifier = Modifier.weight(1f)) {
                        Text(if (adminMode) "메인" else "관리자")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPill(label: String, tone: StatusTone) {
    Surface(color = tone.tint, shape = RoundedCornerShape(999.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(tone.solid, CircleShape)
            )
            Text(
                text = label,
                color = tone.solid,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InfoChip(label: String, active: Boolean) {
    Surface(
        color = if (active) ShinhwaBlueTint else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            color = if (active) ShinhwaBlue else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// 섹션 라벨 — 브랜드 파랑, 마케팅 자료의 eyebrow 라벨 스타일.
// ---------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = ShinhwaBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
}

// ---------------------------------------------------------------------------
// 릴레이 카드
// - 릴레이 실제 상태는 조회 명령이 없으므로 statusLabel에는 반드시
//   "ON 송신됨" / "OFF 송신됨" / "UNKNOWN" / "오류" 계열 문구만 넣는다.
//   ("ON 상태"처럼 단정하는 문구 금지 — STEP_06 안전 조건)
// - ON 버튼은 장비를 움직이는 동작이므로 ACCESS LINK 오렌지 사용.
// ---------------------------------------------------------------------------

@Composable
fun RelayCard(
    title: String,
    statusLabel: String,
    statusTone: StatusTone,
    enabled: Boolean,
    onOn: () -> Unit,
    onOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Surface(color = statusTone.tint, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = statusTone.solid,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onOn,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccessOrange,
                        contentColor = Color.White
                    )
                ) {
                    Text("ON", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOff,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OFF", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ALL OFF — 위험/정지 동작이므로 빨강 아웃라인.
// ---------------------------------------------------------------------------

@Composable
fun AllOffButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
        border = androidx.compose.foundation.BorderStroke(1.dp, FailRed)
    ) {
        Text("ALL OFF", fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// 디지털 입력 타일
// - PRESSED면 카드 전체가 파란 틴트로 변해 멀리서도 상태가 보인다.
// - 최초 수신 전에는 statusText = "UNKNOWN", active = false로 넘긴다.
// ---------------------------------------------------------------------------

@Composable
fun DigitalInputTile(
    label: String,
    statusText: String,
    active: Boolean,
    detail: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (active) ShinhwaBlueTint else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = if (active) {
            androidx.compose.foundation.BorderStroke(1.dp, ShinhwaBlue)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (active) ShinhwaBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) ShinhwaBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = if (active) ShinhwaBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 실시간 로그 카드
// - 고정폭 폰트, TX = 파랑(앱이 말함), RX = 오렌지(장비가 답함), SYS = 회색.
// ---------------------------------------------------------------------------

@Composable
fun LiveLogCard(
    lines: List<DashboardLogLine>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    limit: Int = 5
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("LIVE LOG · 실시간 로그")
            Text(
                text = "전체 보기",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AccessOrange,
                modifier = Modifier.clickable { onViewAll() }
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (lines.isEmpty()) {
                    Text(
                        text = "대기",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    lines.take(limit).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = line.direction,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (line.direction) {
                                    "TX" -> ShinhwaBlue
                                    "RX" -> AccessOrange
                                    else -> WaitGray
                                },
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                text = line.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 하단 탭 바
// - 활성 탭은 ACCESS LINK 오렌지.
// ---------------------------------------------------------------------------

@Composable
fun DashboardBottomBar(
    selected: DashboardTab,
    onSelect: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            DashboardTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
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
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            AccessOrange
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
