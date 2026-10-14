package app.appfredo.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.sin

// Appfredo himself, presenting a tray. Everything is in one 320 x 300 space; he is cropped by its right and
// bottom edges on purpose.
private val ButlerSize = Size(320f, 300f)
private val Head = Offset(238f, 92f)
private const val HeadTilt = -6f
private val Shoulder = Offset(198f, 180f)
private val Elbow = Offset(336f, 290f)
private val Wrist = Offset(182f, 256f)

/** Where the centre of the cloche's rim sits, and how far the cloche is tipped. */
private class Pose(val x: Float, val y: Float, val tilt: Float)

private val OnTheTray = Pose(118f, 223f, 0f)
private val HeldAloft = Pose(104f, 118f, 24f)
private const val KnobHeight = 95f

private val Rays = listOf(
    Offset(96f, 172f) to Offset(88f, 156f),
    Offset(116f, 168f) to Offset(115f, 151f),
    Offset(136f, 171f) to Offset(143f, 156f),
)

private val Torso by lazy {
    listOf(
        Piece("M220,144 C192,148 168,160 162,188 L154,312 L332,312 L332,166 C300,152 278,146 256,144 Z", Tailcoat),
        Piece("M221,146 L255,146 L249,204 L227,204 Z", Linen),
        Piece("M225,194 L238,214 L251,194 L259,312 L217,312 Z", Peel),
        Piece("M238,214 L251,194 L259,312 L238,312 Z", Ripe, alpha = 0.45f),
        Piece("M235,232 C235,230.3 236.3,229 238,229 C239.7,229 241,230.3 241,232 C241,233.7 239.7,235 238,235 C236.3,235 " +
            "235,233.7 235,232 Z M235,252 C235,250.3 236.3,249 238,249 C239.7,249 241,250.3 241,252 C241,253.7 239.7,255 " +
            "238,255 C236.3,255 235,253.7 235,252 Z", Tailcoat),
        Piece("M220,144 L213,184 L226,202 L216,274 L178,274 L178,156 Z", Tailcoat),
        Piece("M256,144 L263,184 L250,202 L260,274 L298,274 L298,156 Z", Tailcoat),
        Piece("M219,149 L212,184 L225,202 M257,149 L264,184 L251,202", TailcoatSoft, stroke = 2.4f),
    )
}

// Head and collar are drawn around the centre of the head, so the head can tilt on its own.
private val HeadPieces by lazy {
    listOf(
        Piece("M-13,30 L13,30 L15,60 L-15,60 Z", SkinShade),
        Piece("M-36,-6 C-41,-8 -46,-4 -46,3 C-46,10 -41,15 -36,13 Z M36,-6 C41,-8 46,-4 46,3 C46,10 41,15 36,13 Z", Skin),
        Piece("M-41,1 Q-39,4 -40,8 M41,1 Q39,4 40,8", SkinShade, stroke = 2f),
        Piece("M0,-40 C22,-40 37,-24 37,-2 C37,22 23,43 0,43 C-23,43 -37,22 -37,-2 C-37,-24 -22,-40 0,-40 Z", Skin),
        Piece("M-37,0 C-45,-32 -27,-56 2,-55 C29,-54 45,-32 37,0 C36,-13 31,-21 22,-25 C6,-18 -10,-21 -20,-33 C-28,-27 -35,-16 " +
            "-37,0 Z", Tailcoat),
        Piece("M-12,-46 C-2,-52 13,-51 25,-43 C12,-47 -1,-48 -12,-46 Z", TailcoatSoft),
        Piece("M-28,12 C-28,9 -24.9,6.5 -21,6.5 C-17.1,6.5 -14,9 -14,12 C-14,15 -17.1,17.5 -21,17.5 C-24.9,17.5 -28,15 -28,12 Z", Blush, alpha = 0.5f),
        Piece("M14,12 C14,9 17.1,6.5 21,6.5 C24.9,6.5 28,9 28,12 C28,15 24.9,17.5 21,17.5 C17.1,17.5 14,15 14,12 Z", Blush, alpha = 0.5f),
        Piece("M-25,-13 Q-16,-20 -7,-14 M7,-14 Q16,-20 25,-13", Tailcoat, stroke = 3.2f),
        Piece("M-24,-3 Q-16,5 -8,-3 M8,-3 Q16,5 24,-3", Tailcoat, stroke = 3.2f),
        Piece("M-2,0 C-3,7 -5,11 0,12 C4,12 5,10 4,8", SkinShade, stroke = 3f),
        Piece("M0,15 C6,11 15,12 21,18 C26,23 33,21 33,12 C37,22 31,31 21,30 C12,29 6,23 0,22 Z M0,15 C-6,11 -15,12 -21,18 " +
            "C-26,23 -33,21 -33,12 C-37,22 -31,31 -21,30 C-12,29 -6,23 0,22 Z", Tailcoat),
        Piece("M-6,31 Q0,35 6,31", Smile, stroke = 2.6f),
    )
}

private val Collar by lazy {
    listOf(
        Piece("M-22,44 L0,62 L-17,68 Z M22,44 L0,62 L17,68 Z", Linen),
        Piece("M-3,63 L-27,51 Q-31,63 -27,76 Z M3,63 L27,51 Q31,63 27,76 Z", Peel),
        Piece("M-3,63 L-27,76 Q-17,71 -3,67 Z M3,63 L27,76 Q17,71 3,67 Z", Ripe),
        Piece("M-4,56 L4,56 Q8,56 8,60 L8,66 Q8,70 4,70 L-4,70 Q-8,70 -8,66 L-8,60 Q-8,56 -4,56 Z", Ripe),
    )
}

private val Tray by lazy {
    listOf(
        Piece("M14,235.5 C14,230.5 60.6,226.5 118,226.5 C175.4,226.5 222,230.5 222,235.5 C222,240.5 175.4,244.5 118,244.5 " +
            "C60.6,244.5 14,240.5 14,235.5 Z", TailcoatDeep),
        Piece("M14,232 C14,227 60.6,223 118,223 C175.4,223 222,227 222,232 C222,237 175.4,241 118,241 C60.6,241 14,237 14,232 Z", Tailcoat),
        Piece("M20,229.8 C20,227.1 63.9,225 118,225 C172.1,225 216,227.1 216,229.8 C216,232.5 172.1,234.6 118,234.6 C63.9,234.6 " +
            "20,232.5 20,229.8 Z", Gold),
        Piece("M22,230.8 C22,228.6 65,226.8 118,226.8 C171,226.8 214,228.6 214,230.8 C214,233 171,234.8 118,234.8 C65,234.8 " +
            "22,233 22,230.8 Z", Tailcoat),
    )
}

private val TrayGlove by lazy { listOf(Piece("M112,244 C112,238 118,236 128,236 L158,236 C168,236 170,247 162,252 C150,259 130,258 119,252 C114,249 112,247 " +
        "112,244 Z", Linen)) }

private val Towel by lazy {
    listOf(
        Piece("M178,238 L212,244 L212,312 L172,312 Z", Linen),
        Piece("M178,238 L188,240 L183,312 L172,312 Z", LinenShade),
        Piece("M192,272 C192,267 195,264 199,264 C203,264 206,267 206,272 Z M190.5,273 L207.5,273 L207.5,275 L190.5,275 Z", Tailcoat),
        Piece("M198.4,280 L193,277.4 L193,283 Z M199.6,280 L205,277.4 L205,283 Z", Ripe),
    )
}

// The cloche is drawn around the centre of its rim, with the dome rising to -y.
private val Dome by lazy {
    listOf(
        Piece("M-66,0 C-66,-52 -36,-80 0,-80 C36,-80 66,-52 66,0 Z", Tailcoat),
        Piece("M-54,-12 C-52,-42 -36,-62 -12,-69 C-30,-56 -41,-38 -43,-12 Z", TailcoatSoft),
        Piece("M-3.5,-88 L3.5,-88 L5,-78 L-5,-78 Z", Tailcoat),
        Piece("M-8,-95 C-8,-99.4 -4.4,-103 0,-103 C4.4,-103 8,-99.4 8,-95 C8,-90.6 4.4,-87 0,-87 C-4.4,-87 -8,-90.6 -8,-95 Z", Tailcoat),
        Piece("M-4.8,-97.4 C-4.8,-98.6 -3.8,-99.6 -2.6,-99.6 C-1.4,-99.6 -0.4,-98.6 -0.4,-97.4 C-0.4,-96.2 -1.4,-95.2 " +
            "-2.6,-95.2 C-3.8,-95.2 -4.8,-96.2 -4.8,-97.4 Z", TailcoatSoft),
        Piece("M-10,-79.5 Q0,-84 10,-79.5 Q0,-76.5 -10,-79.5 Z", Gold),
    )
}

private val RimEdgeOn by lazy {
    listOf(
        Piece("M-70.5,-3 L70.5,-3 Q74,-3 74,0.5 L74,0.5 Q74,4 70.5,4 L-70.5,4 Q-74,4 -74,0.5 L-74,0.5 Q-74,-3 -70.5,-3 Z", Gold),
    )
}

/** The cloche in its own space. At [opening] 0 the rim is seen edge-on; as it grows you look up into the dome. */
private fun DrawScope.cloche(opening: Float) {
    draw(Dome)
    val depth = 15f * opening
    if (depth < 2.2f) {
        draw(RimEdgeOn)
    } else {
        drawOval(Gold, Offset(-70f, -depth - 3f), Size(140f, (depth + 3f) * 2f))
        drawOval(TailcoatDeep, Offset(-64.5f, 0.6f - depth), Size(129f, depth * 2f))
    }
}

/**
 * The butler lifting the cloche. [reveal] runs from 0, cloche on the tray, to 1, held aloft; it is read while
 * drawing, so animating it never recomposes. Values a spring carries past either end simply lift a little
 * further. With [plated] false the tray is bare.
 */
@Composable
internal fun ButlerScene(reveal: () -> Float, modifier: Modifier = Modifier, plated: Boolean = true) {
    Canvas(modifier.aspectRatio(ButlerSize.width / ButlerSize.height)) {
        val lift = reveal()
        scale(size.width / ButlerSize.width, pivot = Offset.Zero) {
            clipRect(0f, 0f, ButlerSize.width, ButlerSize.height) {
                val x = lerp(OnTheTray.x, HeldAloft.x, lift)
                val y = lerp(OnTheTray.y, HeldAloft.y, lift)
                val tilt = lerp(OnTheTray.tilt, HeldAloft.tilt, lift)
                val radians = Math.toRadians(tilt.toDouble())
                val knob = Offset(x + KnobHeight * sin(radians).toFloat(), y - KnobHeight * cos(radians).toFloat())

                draw(Torso)
                withTransform({ translate(Head.x, Head.y); rotate(HeadTilt, Offset.Zero) }) { draw(HeadPieces) }
                translate(Head.x, Head.y) { draw(Collar) }
                // Both sleeves are straight lines, so the raised arm can follow the cloche wherever it goes.
                drawLine(Tailcoat, Shoulder, knob, 27f, StrokeCap.Round)
                drawLine(Tailcoat, Elbow, Wrist, 28f, StrokeCap.Round)
                draw(TrayGlove)
                draw(Tray)
                if (plated) {
                    withTransform({ translate(54.68f, 139.95f); scale(0.66f, 0.66f, Offset.Zero) }) {
                        draw(BananaPieces)
                    }
                    val shine = ((lift - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    if (shine > 0f) {
                        for ((from, to) in Rays) drawLine(Ripe, from, lerp(from, to, shine), 4.5f, StrokeCap.Round, alpha = shine)
                    }
                }
                withTransform({ translate(x, y); rotate(tilt, Offset.Zero) }) { cloche(lift) }
                glove(knob, (knob - Shoulder).let { it / it.getDistance() })
                draw(Towel)
            }
        }
    }
}

/** The white cuff and mitt gripping the knob at [at], at the end of an arm pointing along [along]. */
private fun DrawScope.glove(at: Offset, along: Offset) {
    val across = Offset(-along.y, along.x)
    drawLine(Linen, at - along * 24f, at - along * 15f, 29f, StrokeCap.Butt)
    drawCircle(Linen, 13.5f, at - along * 3f)
    drawCircle(Linen, 6.2f, at - along * 2f + across * 10.5f)
    drawLine(LinenShade, at - along * 14f - across * 5f, at - along * 14f + across * 5f, 2f, StrokeCap.Round)
}

private fun lerp(start: Offset, stop: Offset, fraction: Float) =
    Offset(lerp(start.x, stop.x, fraction), lerp(start.y, stop.y, fraction))

// The tray on its own, for small spots where the butler would not fit.
private val ServingSize = Size(200f, 170f)

private val ServingTrayPieces by lazy {
    listOf(
        Piece("M14,150 C14,145.6 52.5,142 100,142 C147.5,142 186,145.6 186,150 C186,154.4 147.5,158 100,158 C52.5,158 14,154.4 " +
            "14,150 Z", TailcoatDeep),
        Piece("M14,147 C14,142.6 52.5,139 100,139 C147.5,139 186,142.6 186,147 C186,151.4 147.5,155 100,155 C52.5,155 14,151.4 " +
            "14,147 Z", Tailcoat),
        Piece("M20,145 C20,142.7 55.8,140.8 100,140.8 C144.2,140.8 180,142.7 180,145 C180,147.3 144.2,149.2 100,149.2 " +
            "C55.8,149.2 20,147.3 20,145 Z", Gold),
        Piece("M22,146 C22,144.1 56.9,142.6 100,142.6 C143.1,142.6 178,144.1 178,146 C178,147.9 143.1,149.4 100,149.4 " +
            "C56.9,149.4 22,147.9 22,146 Z", Tailcoat),
    )
}

/** A tray with the cloche rising off it. [reveal] and [plated] mean what they do in [ButlerScene]. */
@Composable
internal fun ServingTray(reveal: () -> Float, modifier: Modifier = Modifier, plated: Boolean = true) {
    Canvas(modifier.aspectRatio(ServingSize.width / ServingSize.height)) {
        val lift = reveal()
        scale(size.width / ServingSize.width, pivot = Offset.Zero) {
            draw(ServingTrayPieces)
            if (plated) {
                withTransform({ translate(59.17f, 85.22f); scale(0.43f, 0.43f, Offset.Zero) }) {
                    draw(BananaPieces)
                }
            }
            withTransform({
                translate(lerp(100f, 94f, lift), lerp(140f, 92f, lift))
                rotate(lerp(0f, -13f, lift), Offset.Zero)
                scale(0.8f, 0.8f, Offset.Zero)
            }) { cloche(lift * 0.8f) }
        }
    }
}
