package app.appfredo.ui.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private class IconScope(name: String, mirrored: Boolean) {
    val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirrored)

    fun stroke(pathData: String) {
        builder.addPath(
            addPathNodes(pathData), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun fill(pathData: String) {
        builder.addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
    }
}

private fun icon(name: String, mirrored: Boolean = false, block: IconScope.() -> Unit): ImageVector =
    IconScope(name, mirrored).apply(block).builder.build()

/** Appfredo's own icons: 24dp, two-unit round strokes, tinted by whatever draws them. */
internal object AppfredoIcons {
    val Search: ImageVector by lazy {
        icon("Search") {
            stroke("M17,10.75 C17,14.2 14.2,17 10.75,17 C7.3,17 4.5,14.2 4.5,10.75 C4.5,7.3 7.3,4.5 10.75,4.5 C14.2,4.5 17,7.3 " +
                "17,10.75 Z")
            stroke("M15.4,15.4 L20,20")
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") {
            stroke("M12,5 L12,19 M5,12 L19,12")
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            stroke("M19.07,14.5 C17.95,17.65 14.89,19.68 11.56,19.49 C8.22,19.29 5.42,16.91 4.68,13.66 C3.95,10.4 5.45,7.05 " +
                "8.38,5.43 C11.3,3.82 14.94,4.34 17.3,6.7 L21.2,10.3")
            stroke("M21.2,5.2 L21.2,10.3 L16.1,10.3")
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            stroke("M20.5,12 C20.5,16.69 16.69,20.5 12,20.5 C7.31,20.5 3.5,16.69 3.5,12 C3.5,7.31 7.31,3.5 12,3.5 C16.69,3.5 " +
                "20.5,7.31 20.5,12 Z")
            stroke("M12,11.2 L12,16.4")
            fill("M13.25,7.9 C13.25,8.59 12.69,9.15 12,9.15 C11.31,9.15 10.75,8.59 10.75,7.9 C10.75,7.21 11.31,6.65 12,6.65 " +
                "C12.69,6.65 13.25,7.21 13.25,7.9 Z")
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            stroke("M6.5,6.5 L17.5,17.5 M17.5,6.5 L6.5,17.5")
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            stroke("M12,4 L12,14.8 M7.4,10.4 L12,15 L16.6,10.4")
            stroke("M5,19.5 L19,19.5")
        }
    }

    val External: ImageVector by lazy {
        icon("External", mirrored = true) {
            stroke("M9.2,6 L18,6 L18,14.8 M18,6 L6.5,17.5")
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            stroke("M5.2,12.6 L9.8,17.2 L18.8,7.2")
        }
    }

    val Alert: ImageVector by lazy {
        icon("Alert") {
            stroke("M12,4.6 L20.6,19.2 L3.4,19.2 Z")
            stroke("M12,10 L12,14")
            fill("M13.15,16.6 C13.15,17.24 12.64,17.75 12,17.75 C11.36,17.75 10.85,17.24 10.85,16.6 C10.85,15.96 11.36,15.45 " +
                "12,15.45 C12.64,15.45 13.15,15.96 13.15,16.6 Z")
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            stroke("M12,3.4 L19,6.2 L19,11.4 C19,15.9 16.1,19.2 12,20.6 C7.9,19.2 5,15.9 5,11.4 L5,6.2 Z")
            stroke("M8.8,12 L11.1,14.3 L15.3,9.9")
        }
    }

    val Paste: ImageVector by lazy {
        icon("Paste") {
            stroke("M9,5.2 L7.4,5.2 Q6,5.2 6,6.6 L6,18.6 Q6,20 7.4,20 L16.6,20 Q18,20 18,18.6 L18,6.6 Q18,5.2 16.6,5.2 L15,5.2")
            stroke("M9.9,3.6 L14.1,3.6 Q15,3.6 15,4.5 L15,5.9 Q15,6.8 14.1,6.8 L9.9,6.8 Q9,6.8 9,5.9 L9,4.5 Q9,3.6 9.9,3.6 Z")
            stroke("M9.2,11.4 L14.8,11.4 M9.2,15 L12.8,15")
        }
    }

    val Leaf: ImageVector by lazy {
        icon("Leaf") {
            stroke("M5,19 C5,10.4 10.4,5 19,5 C19,13.6 13.6,19 5,19 Z")
            stroke("M5,19 L12.6,11.4")
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight", mirrored = true) {
            stroke("M9.5,6 L15.5,12 L9.5,18")
        }
    }

    /** The banana as a single-colour silhouette, for places that tint their icons. */
    val Banana: ImageVector by lazy {
        icon("Banana") {
            fill(
                "M2.6,8.2 Q2.9,6.7 2.9,5.6 Q3.6,5.4 4.2,5.7 Q4.1,6.7 4.3,8.3 Z M4.3,8 C4.5,8.3 5.1,9.1 5.4,9.6 C5.7,10.1 5.9,10.5 " +
                    "6.1,10.9 C6.4,11.2 6.8,11.6 7.1,11.9 C7.5,12.3 7.9,12.6 8.3,12.8 C8.7,13.1 9.2,13.4 9.7,13.6 C10.2,13.8 10.7,14 " +
                    "11.3,14.1 C11.9,14.2 12.5,14.3 13.1,14.3 C13.7,14.4 14.4,14.3 15.1,14.3 C15.8,14.2 16.5,14 17.2,13.8 C18,13.6 " +
                    "19.3,13.2 19.7,13.1 Q20.7,12.4 21.5,13.3 Q22,14.3 21,15 C20.6,15.3 19.5,16.5 18.6,17 C17.8,17.5 16.8,17.8 " +
                    "15.9,18.1 C15,18.4 14.1,18.5 13.2,18.6 C12.3,18.6 11.3,18.6 10.5,18.4 C9.6,18.3 8.8,18.1 8,17.8 C7.2,17.4 6.4,17 " +
                    "5.7,16.6 C5.1,16.1 4.4,15.5 3.9,14.9 C3.4,14.2 2.9,13.5 2.6,12.8 C2.3,12 2,11.2 2,10.4 C2,9.6 2.5,8.4 2.6,8 Z",
            )
        }
    }
}
