package com.zly.mentalmath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BackButton(onClick: () -> Unit) {
    val color = LocalContentColor.current
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "返回" }
    ) {
        Canvas(Modifier.size(24.dp)) {
            val top = Offset(size.width * 0.65f, size.height * 0.2f)
            val middle = Offset(size.width * 0.35f, size.height * 0.5f)
            val bottom = Offset(size.width * 0.65f, size.height * 0.8f)
            drawLine(color, top, middle, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, middle, bottom, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
