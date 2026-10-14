package app.appfredo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.appfredo.R

// Both faces are variable fonts under the SIL Open Font License; see licenses/.
private fun fraunces(weight: Int) = Font(
    R.font.fraunces, FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", 72f),
        // Fully soft and never wonky: the rounded Cooper-ish cut, without the quirky alternates.
        FontVariation.Setting("SOFT", 100f),
        FontVariation.Setting("WONK", 0f),
    ),
)

private fun figtree(weight: Int) =
    Font(R.font.figtree, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** The butler's voice: headlines and anything he says out loud. */
internal val Fraunces = FontFamily(fraunces(600), fraunces(700), fraunces(800), fraunces(900))

/** Everything you read or tap. */
internal val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700), figtree(800))

private fun voice(size: Int, line: Int, weight: Int, tracking: Double) = TextStyle(
    fontFamily = Fraunces, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

private fun text(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Figtree, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

private fun TextStyle.heavier() = copy(fontWeight = FontWeight(minOf((fontWeight?.weight ?: 400) + 100, 900)))

private val DisplayLarge = voice(52, 56, 800, -1.0)
private val DisplayMedium = voice(44, 48, 800, -0.8)
private val DisplaySmall = voice(36, 40, 800, -0.6)
private val HeadlineLarge = voice(32, 36, 800, -0.5)
private val HeadlineMedium = voice(28, 32, 700, -0.4)
private val HeadlineSmall = voice(24, 28, 700, -0.3)
private val TitleLarge = voice(21, 26, 700, -0.2)
private val TitleMedium = text(16, 22, 700)
private val TitleSmall = text(14, 20, 700, 0.1)
private val BodyLarge = text(16, 24, 400)
private val BodyMedium = text(14, 20, 400)
private val BodySmall = text(12, 16, 500, 0.1)
private val LabelLarge = text(14, 20, 700, 0.1)
private val LabelMedium = text(12, 16, 600, 0.2)

/** Small caps-style overlines; set the text in capitals at the call site. */
private val LabelSmall = text(11, 16, 700, 1.4)

internal val AppfredoTypography = Typography(
    displayLarge = DisplayLarge, displayMedium = DisplayMedium, displaySmall = DisplaySmall,
    headlineLarge = HeadlineLarge, headlineMedium = HeadlineMedium, headlineSmall = HeadlineSmall,
    titleLarge = TitleLarge, titleMedium = TitleMedium, titleSmall = TitleSmall,
    bodyLarge = BodyLarge, bodyMedium = BodyMedium, bodySmall = BodySmall,
    labelLarge = LabelLarge, labelMedium = LabelMedium, labelSmall = LabelSmall,
    displayLargeEmphasized = DisplayLarge.heavier(), displayMediumEmphasized = DisplayMedium.heavier(),
    displaySmallEmphasized = DisplaySmall.heavier(), headlineLargeEmphasized = HeadlineLarge.heavier(),
    headlineMediumEmphasized = HeadlineMedium.heavier(), headlineSmallEmphasized = HeadlineSmall.heavier(),
    titleLargeEmphasized = TitleLarge.heavier(), titleMediumEmphasized = TitleMedium.heavier(),
    titleSmallEmphasized = TitleSmall.heavier(), bodyLargeEmphasized = BodyLarge.heavier(),
    bodyMediumEmphasized = BodyMedium.heavier(), bodySmallEmphasized = BodySmall.heavier(),
    labelLargeEmphasized = LabelLarge.heavier(), labelMediumEmphasized = LabelMedium.heavier(),
    labelSmallEmphasized = LabelSmall.heavier(),
)
