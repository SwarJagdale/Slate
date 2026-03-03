package app.webcodex.codex.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.webcodex.codex.storage.AppSettings

// ─── Extra design-token colours not in M3 ────────────────────────────────────
data class CodexColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val borderHover: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val accent: Color,
    val accentDim: Color,
    val green: Color,
    val greenDim: Color,
    val red: Color,
    val redDim: Color,
    val blue: Color,
    val blueDim: Color,
    val yellow: Color,
    val yellowDim: Color,
    val orange: Color,
    val orangeDim: Color
)

// ─── Spacing tokens ───────────────────────────────────────────────────────────
data class CodexSpacing(
    val tiny: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp
)

// ─── Radius tokens ────────────────────────────────────────────────────────────
data class CodexRadius(
    val small: Dp = 6.dp,
    val medium: Dp = 10.dp,
    val large: Dp = 14.dp,
    val extraLarge: Dp = 20.dp
)

val LocalCodexColors = staticCompositionLocalOf {
    CodexColors(
        bg = Color(0xFF050507), surface = Color(0xFF0c0c0f), surface2 = Color(0xFF141418),
        surface3 = Color(0xFF1c1c22), border = Color(0xFF2e2e36), borderHover = Color(0xFF42424e),
        text = Color(0xFFf0f0f2), text2 = Color(0xFFb8b8c2), muted = Color(0xFF6e6e7a),
        accent = Color(0xFFe0a84a), accentDim = Color(0x2ee0a84a),
        green = Color(0xFF52e88a), greenDim = Color(0x2452e88a),
        red = Color(0xFFf97575), redDim = Color(0x24f97575),
        blue = Color(0xFF6bb4ff), blueDim = Color(0x246bb4ff),
        yellow = Color(0xFFfcd34d), yellowDim = Color(0x24fcd34d),
        orange = Color(0xFFfb9f4a), orangeDim = Color(0x24fb9f4a)
    )
}
val LocalCodexSpacing = staticCompositionLocalOf { CodexSpacing() }
val LocalCodexRadius = staticCompositionLocalOf { CodexRadius() }
val LocalCodexLineWidth = staticCompositionLocalOf { 720.dp }

// ─── Access helpers ───────────────────────────────────────────────────────────
object CodexTheme {
    val colors: CodexColors @Composable get() = LocalCodexColors.current
    val spacing: CodexSpacing @Composable get() = LocalCodexSpacing.current
    val radius: CodexRadius @Composable get() = LocalCodexRadius.current
    val lineWidth: Dp @Composable get() = LocalCodexLineWidth.current
}

// ─── Theme presets (mirrors web THEME_PRESETS exactly) ───────────────────────
private fun preset(
    bg: Long, surface: Long, surface2: Long, surface3: Long,
    border: Long, borderHover: Long,
    text: Long, text2: Long, muted: Long,
    accent: Long, accentDim: Long,
    green: Long, greenDim: Long,
    red: Long, redDim: Long,
    blue: Long, blueDim: Long,
    yellow: Long, yellowDim: Long,
    orange: Long, orangeDim: Long
) = CodexColors(
    bg = Color(bg), surface = Color(surface), surface2 = Color(surface2), surface3 = Color(surface3),
    border = Color(border), borderHover = Color(borderHover),
    text = Color(text), text2 = Color(text2), muted = Color(muted),
    accent = Color(accent), accentDim = Color(accentDim),
    green = Color(green), greenDim = Color(greenDim),
    red = Color(red), redDim = Color(redDim),
    blue = Color(blue), blueDim = Color(blueDim),
    yellow = Color(yellow), yellowDim = Color(yellowDim),
    orange = Color(orange), orangeDim = Color(orangeDim)
)

private val THEME_DEFAULT = preset(
    bg=0xFF050507, surface=0xFF0c0c0f, surface2=0xFF141418, surface3=0xFF1c1c22,
    border=0xFF2e2e36, borderHover=0xFF42424e,
    text=0xFFf0f0f2, text2=0xFFb8b8c2, muted=0xFF6e6e7a,
    accent=0xFFe0a84a, accentDim=0x2ee0a84a,
    green=0xFF52e88a, greenDim=0x2452e88a,
    red=0xFFf97575, redDim=0x24f97575,
    blue=0xFF6bb4ff, blueDim=0x246bb4ff,
    yellow=0xFFfcd34d, yellowDim=0x24fcd34d,
    orange=0xFFfb9f4a, orangeDim=0x24fb9f4a
)

private val THEME_AMOLED = preset(
    bg=0xFF000000, surface=0xFF0a0a0a, surface2=0xFF111111, surface3=0xFF1a1a1a,
    border=0xFF262626, borderHover=0xFF404040,
    text=0xFFf5f5f5, text2=0xFFc4c4c4, muted=0xFF737373,
    accent=0xFFf59e0b, accentDim=0x33f59e0b,
    green=0xFF22c55e, greenDim=0x2622c55e,
    red=0xFFef4444, redDim=0x26ef4444,
    blue=0xFF3b82f6, blueDim=0x263b82f6,
    yellow=0xFFeab308, yellowDim=0x26eab308,
    orange=0xFFf97316, orangeDim=0x26f97316
)

private val THEME_TOKYO_NIGHT = preset(
    bg=0xFF0f0f14, surface=0xFF1c1e26, surface2=0xFF252834, surface3=0xFF2f3342,
    border=0xFF3b3f52, borderHover=0xFF4d5270,
    text=0xFFd4d8f0, text2=0xFFa8add8, muted=0xFF565f89,
    accent=0xFF7dcfff, accentDim=0x2e7dcfff,
    green=0xFF9ece6a, greenDim=0x249ece6a,
    red=0xFFf7768e, redDim=0x24f7768e,
    blue=0xFF7aa2f7, blueDim=0x247aa2f7,
    yellow=0xFFe0af68, yellowDim=0x24e0af68,
    orange=0xFFff9e64, orangeDim=0x24ff9e64
)

private val THEME_CATPPUCCIN_MOCHA = preset(
    bg=0xFF12121a, surface=0xFF1e1e2e, surface2=0xFF2d2d3d, surface3=0xFF3d3d50,
    border=0xFF5a5a6e, borderHover=0xFF6e6e84,
    text=0xFFe0e4f0, text2=0xFFb8bcd4, muted=0xFF6c7086,
    accent=0xFFddb4f8, accentDim=0x2eddb4f8,
    green=0xFFa6e3a1, greenDim=0x24a6e3a1,
    red=0xFFf38ba8, redDim=0x24f38ba8,
    blue=0xFF89b4fa, blueDim=0x2489b4fa,
    yellow=0xFFf9e2af, yellowDim=0x24f9e2af,
    orange=0xFFfab387, orangeDim=0x24fab387
)

private val THEME_CATPPUCCIN_LATTE = preset(
    bg=0xFFe6e9ef, surface=0xFFdce0e8, surface2=0xFFccd0da, surface3=0xFFbcc0cc,
    border=0xFF9ca0b0, borderHover=0xFF8c8fa1,
    text=0xFF2c2e34, text2=0xFF4c4f69, muted=0xFF7c7f93,
    accent=0xFF7c3aed, accentDim=0x267c3aed,
    green=0xFF16a34a, greenDim=0x2016a34a,
    red=0xFFb91c1c, redDim=0x20b91c1c,
    blue=0xFF1d4ed8, blueDim=0x201d4ed8,
    yellow=0xFFb45309, yellowDim=0x20b45309,
    orange=0xFFc2410c, orangeDim=0x20c2410c
)

private val THEME_DRACULA = preset(
    bg=0xFF1e1f29, surface=0xFF282a36, surface2=0xFF343746, surface3=0xFF424450,
    border=0xFF6272a4, borderHover=0xFF818cf8,
    text=0xFFf8f8f2, text2=0xFFe8e8e4, muted=0xFF6272a4,
    accent=0xFFbd93f9, accentDim=0x33bd93f9,
    green=0xFF50fa7b, greenDim=0x2450fa7b,
    red=0xFFff5555, redDim=0x24ff5555,
    blue=0xFF8be9fd, blueDim=0x248be9fd,
    yellow=0xFFf1fa8c, yellowDim=0x24f1fa8c,
    orange=0xFFffb86c, orangeDim=0x24ffb86c
)

private val THEME_NORD = preset(
    bg=0xFF1c1f26, surface=0xFF2e3440, surface2=0xFF3b4252, surface3=0xFF434c5e,
    border=0xFF4c566a, borderHover=0xFF5e81ac,
    text=0xFFeceff4, text2=0xFFd8dee9, muted=0xFF4c566a,
    accent=0xFF88c0d0, accentDim=0x2e88c0d0,
    green=0xFFa3be8c, greenDim=0x24a3be8c,
    red=0xFFbf616a, redDim=0x24bf616a,
    blue=0xFF81a1c1, blueDim=0x2481a1c1,
    yellow=0xFFebcb8b, yellowDim=0x24ebcb8b,
    orange=0xFFd08770, orangeDim=0x24d08770
)

private val THEME_GRUVBOX = preset(
    bg=0xFF1d2021, surface=0xFF282828, surface2=0xFF3c3836, surface3=0xFF504945,
    border=0xFF665c54, borderHover=0xFF928374,
    text=0xFFf2e5bc, text2=0xFFd5c4a1, muted=0xFF928374,
    accent=0xFFfabd2f, accentDim=0x33fabd2f,
    green=0xFFb8bb26, greenDim=0x24b8bb26,
    red=0xFFfb4934, redDim=0x24fb4934,
    blue=0xFF83a598, blueDim=0x2483a598,
    yellow=0xFFfabd2f, yellowDim=0x24fabd2f,
    orange=0xFFfe8019, orangeDim=0x24fe8019
)

private val THEME_LIGHT = preset(
    bg=0xFFf5f5f5, surface=0xFFeeeeee, surface2=0xFFe0e0e0, surface3=0xFFd0d0d0,
    border=0xFF9e9e9e, borderHover=0xFF757575,
    text=0xFF0d0d0d, text2=0xFF2d2d2d, muted=0xFF616161,
    accent=0xFF1565c0, accentDim=0x201565c0,
    green=0xFF2e7d32, greenDim=0x1a2e7d32,
    red=0xFFc62828, redDim=0x1ac62828,
    blue=0xFF1565c0, blueDim=0x1a1565c0,
    yellow=0xFFf9a825, yellowDim=0x1af9a825,
    orange=0xFFef6c00, orangeDim=0x1aef6c00
)

private fun themeColors(theme: String): CodexColors = when (theme) {
    "amoled" -> THEME_AMOLED
    "tokyo-night" -> THEME_TOKYO_NIGHT
    "catppuccin-mocha" -> THEME_CATPPUCCIN_MOCHA
    "catppuccin-latte" -> THEME_CATPPUCCIN_LATTE
    "dracula" -> THEME_DRACULA
    "nord" -> THEME_NORD
    "gruvbox" -> THEME_GRUVBOX
    "light" -> THEME_LIGHT
    else -> THEME_DEFAULT
}

private fun isLightTheme(theme: String) = theme == "catppuccin-latte" || theme == "light"

private fun buildColorScheme(c: CodexColors, light: Boolean): ColorScheme = if (light) {
    lightColorScheme(
        primary = c.accent,
        onPrimary = c.bg,
        primaryContainer = c.accentDim,
        onPrimaryContainer = c.accent,
        background = c.bg,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.text2,
        outline = c.border,
        outlineVariant = c.surface3,
        error = c.red,
        onError = c.bg,
        errorContainer = c.redDim,
        onErrorContainer = c.red,
        secondaryContainer = c.surface3,
        onSecondaryContainer = c.text
    )
} else {
    darkColorScheme(
        primary = c.accent,
        onPrimary = c.bg,
        primaryContainer = c.accentDim,
        onPrimaryContainer = c.accent,
        background = c.bg,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.text2,
        outline = c.border,
        outlineVariant = c.surface3,
        error = c.red,
        onError = c.bg,
        errorContainer = c.redDim,
        onErrorContainer = c.red,
        secondaryContainer = c.surface3,
        onSecondaryContainer = c.text
    )
}

private fun fontFamily(font: String): FontFamily = when (font) {
    "fira", "ibm" -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

private fun buildTypography(font: String, fontSize: Float, lineHeightMultiplier: Float): Typography {
    val ff = fontFamily(font)
    val mono = FontFamily.Monospace
    val base = fontSize.sp
    val lineH = (fontSize * lineHeightMultiplier).sp
    val sm = (fontSize * 0.857f).sp
    val smLineH = (fontSize * 0.857f * lineHeightMultiplier).sp
    val lg = (fontSize * 1.143f).sp
    val xl = (fontSize * 1.286f).sp
    return Typography(
        bodyLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = base, lineHeight = lineH),
        bodyMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = base, lineHeight = lineH),
        bodySmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = sm, lineHeight = smLineH),
        labelLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = sm, lineHeight = smLineH),
        labelMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = sm, lineHeight = smLineH),
        labelSmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = (fontSize * 0.786f).sp, lineHeight = (fontSize * 0.786f * lineHeightMultiplier).sp),
        titleLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = xl, lineHeight = (fontSize * 1.286f * lineHeightMultiplier).sp),
        titleMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = lg, lineHeight = (fontSize * 1.143f * lineHeightMultiplier).sp),
        titleSmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = base, lineHeight = lineH),
        headlineMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = (fontSize * 1.5f).sp, lineHeight = (fontSize * 1.5f * lineHeightMultiplier).sp)
    )
}

// ─── Main theme composable ────────────────────────────────────────────────────
@Composable
fun CodexTheme(
    settings: AppSettings = AppSettings("", "", "on-request", "workspaceWrite", "10.0.2.2", "3000"),
    content: @Composable () -> Unit
) {
    val c = themeColors(settings.theme)
    val light = isLightTheme(settings.theme)
    val colorScheme = buildColorScheme(c, light)
    val typography = buildTypography(settings.font, settings.fontSize, settings.lineHeight)
    val radius = CodexRadius(
        small = (settings.borderRadius * 0.5f).dp.coerceAtLeast(2.dp),
        medium = settings.borderRadius.dp,
        large = (settings.borderRadius * 1.4f).dp,
        extraLarge = (settings.borderRadius * 2f).dp
    )

    CompositionLocalProvider(
        LocalCodexColors provides c,
        LocalCodexSpacing provides CodexSpacing(),
        LocalCodexRadius provides radius,
        LocalCodexLineWidth provides settings.lineWidth.dp
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
