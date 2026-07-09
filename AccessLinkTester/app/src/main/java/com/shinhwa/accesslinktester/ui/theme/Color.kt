package com.shinhwa.accesslinktester.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// 브랜드 컬러 (사내 마케팅 자료 v1.0 기준)
// 파랑 = 소프트웨어/앱 영역, 오렌지 = ACCESS LINK 장비/브릿지 영역
// ---------------------------------------------------------------------------

val ShinhwaBlue = Color(0xFF378ADD)        // 브랜드 메인 파랑
val ShinhwaBlueDeep = Color(0xFF185FA5)    // 진한 파랑 (텍스트/강조)
val ShinhwaBlueTint = Color(0xFFE6F1FB)    // 연한 파랑 (배경 틴트)

val AccessOrange = Color(0xFFD85A30)       // ACCESS LINK 오렌지
val AccessOrangeDeep = Color(0xFF993C1D)   // 진한 오렌지
val AccessOrangeTint = Color(0xFFFAECE7)   // 연한 오렌지 (배경 틴트)

// ---------------------------------------------------------------------------
// 상태 컬러 (docs/project-notes.md 상태 색상 규칙)
// 정상/PASS = 초록, 실패/FAIL = 빨강, 진행 중/활성 = 파랑, 대기/미확인 = 회색
// 브랜드 색보다 안전 의미가 우선이므로 변경하지 않는다.
// ---------------------------------------------------------------------------

val PassGreen = Color(0xFF1D9E75)
val PassGreenTint = Color(0xFFE1F5EE)
val FailRed = Color(0xFFE24B4A)
val FailRedTint = Color(0xFFFCEBEB)
val ActiveBlue = ShinhwaBlue               // 활성 = 브랜드 파랑과 통일
val WaitGray = Color(0xFF888780)
val WaitGrayTint = Color(0xFFF1F1EF)

// ---------------------------------------------------------------------------
// 서피스 / 텍스트
// ---------------------------------------------------------------------------

val AppBackground = Color(0xFFF5F7FA)      // 화면 배경
val CardSurface = Color(0xFFFFFFFF)        // 카드 배경
val SubtleSurface = Color(0xFFF8FAFC)      // 카드 내부 서브 배경
val TextPrimary = Color(0xFF1A1F27)
val TextSecondary = Color(0xFF5B6472)
val TextMuted = Color(0xFF9AA3AF)
val HairlineBorder = Color(0xFFE4E8EE)

// 다크 모드용 (2차 적용 대상 — Theme.kt 참고)
val DarkBackground = Color(0xFF14181E)
val DarkCardSurface = Color(0xFF1D232B)
val DarkSubtleSurface = Color(0xFF242B34)
val DarkTextPrimary = Color(0xFFEDF0F4)
val DarkTextSecondary = Color(0xFFA6AFBB)
