package app.appfredo.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

private val EmblemDome by lazy {
    PathParser().parsePathString(
        "M7.5,27 C7.5,16.2 14.9,9.6 24,9.6 C33.1,9.6 40.5,16.2 40.5,27 Z M21.3,6.3 C21.3,4.8 22.5,3.6 24,3.6 C25.5,3.6 " +
            "26.7,4.8 26.7,6.3 C26.7,7.8 25.5,9 24,9 C22.5,9 21.3,7.8 21.3,6.3 Z M23.5,7.5 L24.5,7.5 Q25.1,7.5 25.1,8.1 " +
            "L25.1,10.1 Q25.1,10.7 24.5,10.7 L23.5,10.7 Q22.9,10.7 22.9,10.1 L22.9,8.1 Q22.9,7.5 23.5,7.5 Z M6.3,28.6 " +
            "L41.7,28.6 Q43.5,28.6 43.5,30.4 L43.5,30.4 Q43.5,32.2 41.7,32.2 L6.3,32.2 Q4.5,32.2 4.5,30.4 L4.5,30.4 Q4.5,28.6 " +
            "6.3,28.6 Z",
    ).toPath()
}

private val EmblemTie by lazy {
    PathParser().parsePathString(
        "M22.7,39 L14.2,34.4 Q12.9,39 14.2,43.6 Z M25.3,39 L33.8,34.4 Q35.1,39 33.8,43.6 Z M23.2,36.6 L24.8,36.6 " +
            "Q26.1,36.6 26.1,37.9 L26.1,40.1 Q26.1,41.4 24.8,41.4 L23.2,41.4 Q21.9,41.4 21.9,40.1 L21.9,37.9 Q21.9,36.6 " +
            "23.2,36.6 Z",
    ).toPath()
}

/** The mark: a cloche over a bow tie. Two colours, because the tie has to stand out from whatever is behind it. */
@Composable
internal fun Emblem(dome: Color, tie: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        scale(size.minDimension / 48f, pivot = Offset.Zero) {
            drawPath(EmblemDome, dome)
            drawPath(EmblemTie, tie)
        }
    }
}
