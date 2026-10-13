package org.agora.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons of the web app's navigation (Lucide style, assets/index.html #bottom-nav): thin rounded strokes
 * instead of the heavier Material symbols, so the bar looks as soft as on the web.
 */
object SoftIcons {
    val Home: ImageVector by lazy { line("home", "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10") }
    val Finances: ImageVector by lazy { line("finances", "M12 1v22", "M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6") }
    val Events: ImageVector by lazy {
        line("events", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M16 2v4", "M8 2v4", "M3 10h18")
    }
    val Mentoring: ImageVector by lazy {
        line(
            "mentoring", "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2", "M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
            "M23 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75"
        )
    }
    val Extras: ImageVector by lazy {
        fun tile(x: Int, y: Int) = "M${x + 2} ${y}h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z"
        line("extras", tile(3, 3), tile(14, 3), tile(3, 14), tile(14, 14))
    }

    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()
}
