package com.localai.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun nodes(d: String) = PathParser().parsePathString(d).toNodes()

private fun line(name: String, vararg d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach {
            addPath(
                nodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun solid(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(nodes(d), fill = SolidColor(Color.Black))
    }.build()

/** 全部是矢量图标，用代码绘制，没有任何表情符号或位图。 */
object Ic {
    val Send = solid("send", "M3,20.5l18.5,-8.5L3,3.5v6.6l12,1.9l-12,1.9z")
    val Stop = solid("stop", "M7.5,7.5h9v9h-9z")
    val Add = line("add", "M12,5v14", "M5,12h14")
    val Sliders = line("sliders", "M4,7h16", "M15,4v6", "M4,17h16", "M9,14v6")
    val Globe = line(
        "globe", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z", "M3,12h18",
        "M12,3c3,2.5 3,15.5 0,18c-3,-2.5 -3,-15.5 0,-18z",
    )
    val Chevron = line("chevron", "M6,9l6,6l6,-6")
    val Copy = line("copy", "M9,9h10v11H9z", "M15,5H5v10")
    val Trash = line("trash", "M4,7h16", "M10,4h4", "M6,7l1,13h10l1,-13", "M10,11v6", "M14,11v6")
    val Check = line("check", "M5,12l5,5L20,7")
    val Import = line("import", "M12,4v11", "M7,10l5,5l5,-5", "M5,20h14")
    val Back = line("back", "M19,12H5", "M11,6l-6,6l6,6")
    val Clock = line("clock", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z", "M12,7v5l3,2")
}
