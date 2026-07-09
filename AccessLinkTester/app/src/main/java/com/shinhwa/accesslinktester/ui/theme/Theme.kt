package com.shinhwa.accesslinktester.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// AccessLinkTester 브랜드 테마
// - 기본 Purple 테마와 dynamicColor를 제거하고 브랜드 색으로 고정한다.
//   (dynamicColor는 기기 배경화면 색이 브랜드 색을 덮어쓰므로 사용하지 않음)
// - primary = 신화시스템 파랑, tertiary = ACCESS LINK 오렌지
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = ShinhwaBlue,
    onPrimary = CardSurface,
    primaryContainer = ShinhwaBlueTint,
    onPrimaryContainer = ShinhwaBlueDeep,
    secondary = TextSecondary,
    onSecondary = CardSurface,
    tertiary = AccessOrange,
    onTertiary = CardSurface,
    tertiaryContainer = AccessOrangeTint,
    onTertiaryContainer = AccessOrangeDeep,
    error = FailRed,
    onError = CardSurface,
    errorContainer = FailRedTint,
    onErrorContainer = FailRed,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SubtleSurface,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder
)

private val DarkColors = darkColorScheme(
    primary = ShinhwaBlue,
    onPrimary = DarkBackground,
    primaryContainer = ShinhwaBlueDeep,
    onPrimaryContainer = ShinhwaBlueTint,
    secondary = DarkTextSecondary,
    onSecondary = DarkBackground,
    tertiary = AccessOrange,
    onTertiary = DarkBackground,
    tertiaryContainer = AccessOrangeDeep,
    onTertiaryContainer = AccessOrangeTint,
    error = FailRed,
    onError = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCardSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSubtleSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkSubtleSurface
)

@Composable
fun AccessLinkTesterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
