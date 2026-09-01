package com.maxtime.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import maxtime.shared.generated.resources.Res
import maxtime.shared.generated.resources.satoshi_black
import maxtime.shared.generated.resources.satoshi_black_italic
import maxtime.shared.generated.resources.satoshi_bold
import maxtime.shared.generated.resources.satoshi_bold_italic
import maxtime.shared.generated.resources.satoshi_italic
import maxtime.shared.generated.resources.satoshi_light
import maxtime.shared.generated.resources.satoshi_light_italic
import maxtime.shared.generated.resources.satoshi_medium
import maxtime.shared.generated.resources.satoshi_medium_italic
import maxtime.shared.generated.resources.satoshi_regular
import org.jetbrains.compose.resources.Font

/** Design tokens straight from the locked prototype (Section 4 of the handoff doc). Do not
 *  redesign colors, typography, card language or spacing — this is an implementation pass. */
object MaxTimeColors {
    val bg1 = Color(0xFF0A0C07)
    val bg2 = Color(0xFF10130A)
    val bg3 = Color(0xFF151810)
    val bgTop = Color(0xFF1A2010)

    val textStrong = Color(0xFFF6FAEC)
    val text = Color(0xFFE3E9D4)
    val muted = Color(0xFF8A9174)
    val muted2 = Color(0xFFC6D1A4)

    val accent = Color(0xFFC6FF3D)
    val accent2 = Color(0xFFE2FF8E)
    val accentDeep = Color(0xFF7FAE10)
    val accentDim = Color(0xFF4D5C22)
    val onAccent = Color(0xFF0C1005)

    val accentWash = Color(0x0FC6FF3D)   // rgba(198,255,61,.06)
    val accent2Wash = Color(0x1EE2FF8E)  // rgba(226,255,142,.12)
    val glowSoft = Color(0x47C6FF3D)     // rgba(198,255,61,.28)
    val glowMid = Color(0x66C6FF3D)      // rgba(198,255,61,.4)

    val cardBg = Color(0xFF12150C)
    val cardBorder = Color(0xFF262C17)
    val chipBorder = Color(0xFF2A3018)

    val red = Color(0xFFFF5A5F)
    val redWash = Color(0x14FF5A5F)
    val amber = Color(0xFFFFB27A)
    val amberWash = Color(0x14FFB27A)

    val screenGradient = Brush.radialGradient(
        colorStops = arrayOf(
            0f to bgTop,
            0.45f to bg2,
            1f to bg1
        )
    )
}

val LocalMaxTimeFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

object MaxTimeType {
    val heading: TextStyle
        @Composable get() = TextStyle(fontFamily = LocalMaxTimeFontFamily.current, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaxTimeColors.textStrong, letterSpacing = (-0.2).sp)
    val sectionLabel: TextStyle
        @Composable get() = TextStyle(fontFamily = LocalMaxTimeFontFamily.current, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaxTimeColors.textStrong)
    val metaLabel: TextStyle
        @Composable get() = TextStyle(fontFamily = LocalMaxTimeFontFamily.current, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaxTimeColors.muted2, letterSpacing = 0.5.sp)
    val body: TextStyle
        @Composable get() = TextStyle(fontFamily = LocalMaxTimeFontFamily.current, fontSize = 13.sp, fontWeight = FontWeight.Normal, color = MaxTimeColors.muted2, lineHeight = 19.sp)
    val buttonText: TextStyle
        @Composable get() = TextStyle(fontFamily = LocalMaxTimeFontFamily.current, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val caption: TextStyle
        @Composable get() = TextStyle(fontFamily = LocalMaxTimeFontFamily.current, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaxTimeColors.muted)
}

object MaxTimeShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(50)
    val modal = RoundedCornerShape(26.dp)
}

object MaxTimeSpacing {
    val screenPadding = 20.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 14.dp
    val lg = 20.dp
}

@Composable
fun MaxTimeTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = MaxTimeColors.accent,
        onPrimary = MaxTimeColors.onAccent,
        background = MaxTimeColors.bg1,
        onBackground = MaxTimeColors.textStrong,
        surface = MaxTimeColors.cardBg,
        onSurface = MaxTimeColors.textStrong,
        error = MaxTimeColors.red
    )
    val satoshi = FontFamily(
        Font(Res.font.satoshi_light, FontWeight.Light, FontStyle.Normal),
        Font(Res.font.satoshi_light_italic, FontWeight.Light, FontStyle.Italic),
        Font(Res.font.satoshi_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.satoshi_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.satoshi_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.satoshi_medium_italic, FontWeight.Medium, FontStyle.Italic),
        Font(Res.font.satoshi_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.satoshi_bold_italic, FontWeight.Bold, FontStyle.Italic),
        Font(Res.font.satoshi_black, FontWeight.Black, FontStyle.Normal),
        Font(Res.font.satoshi_black_italic, FontWeight.Black, FontStyle.Italic),
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = satoshi),
        displayMedium = base.displayMedium.copy(fontFamily = satoshi),
        displaySmall = base.displaySmall.copy(fontFamily = satoshi),
        headlineLarge = base.headlineLarge.copy(fontFamily = satoshi),
        headlineMedium = base.headlineMedium.copy(fontFamily = satoshi),
        headlineSmall = base.headlineSmall.copy(fontFamily = satoshi),
        titleLarge = base.titleLarge.copy(fontFamily = satoshi),
        titleMedium = base.titleMedium.copy(fontFamily = satoshi),
        titleSmall = base.titleSmall.copy(fontFamily = satoshi),
        bodyLarge = base.bodyLarge.copy(fontFamily = satoshi),
        bodyMedium = base.bodyMedium.copy(fontFamily = satoshi),
        bodySmall = base.bodySmall.copy(fontFamily = satoshi),
        labelLarge = base.labelLarge.copy(fontFamily = satoshi),
        labelMedium = base.labelMedium.copy(fontFamily = satoshi),
        labelSmall = base.labelSmall.copy(fontFamily = satoshi),
    )
    CompositionLocalProvider(LocalMaxTimeFontFamily provides satoshi) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
