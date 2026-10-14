package app.appfredo.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import app.appfredo.ui.theme.Banana

// The illustrations' own colours. They do not follow the theme: the art always stands on a pale spotlight,
// so one set of inks works in light and dark.
internal val Peel = Banana.Peel
internal val Ripe = Banana.Ripe
internal val Shine = Color(0xFFFFF0A8)
internal val Stem = Color(0xFFB58A14)
internal val StemTip = Color(0xFF5C4510)
internal val Tip = Color(0xFF4A3810)
internal val Tailcoat = Banana.Tailcoat
internal val TailcoatSoft = Color(0xFF4A4230)
internal val TailcoatDeep = Color(0xFF120F08)
internal val Gold = Color(0xFFE9B60E)
internal val Linen = Banana.Linen
internal val LinenShade = Color(0xFFEADFC2)
internal val Skin = Color(0xFFFFE2A0)
internal val SkinShade = Color(0xFFF0C063)
internal val Blush = Color(0xFFFFB14F)
internal val Smile = Color(0xFFB4761A)
internal val PeelInside = Color(0xFFFFF3BF)
internal val PeelInsideShade = Color(0xFFF7DF8C)

/** One flat shape of an illustration, in that illustration's own units. A [stroke] of zero means filled. */
internal class Piece(
    pathData: String,
    val color: Color,
    val stroke: Float = 0f,
    val cap: StrokeCap = StrokeCap.Round,
    val alpha: Float = 1f,
) {
    val path: Path = PathParser().parsePathString(pathData).toPath()
}

internal fun DrawScope.draw(pieces: List<Piece>) {
    for (piece in pieces) {
        if (piece.stroke == 0f) drawPath(piece.path, piece.color, piece.alpha)
        else drawPath(piece.path, piece.color, piece.alpha, Stroke(piece.stroke, cap = piece.cap, join = StrokeJoin.Round))
    }
}

/** A still illustration that scales to whatever box it is given. */
internal class ArtPainter(private val viewport: Size, private val pieces: List<Piece>) : Painter() {
    override val intrinsicSize: Size get() = viewport

    override fun DrawScope.onDraw() {
        scale(size.width / viewport.width, size.height / viewport.height, pivot = Offset.Zero) { draw(pieces) }
    }
}
