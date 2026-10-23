package id.kasirq.dailysunnah.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BackNavigationButton(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "Kembali" },
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = size.minDimension * 0.085f
            val centerY = size.height / 2f
            val leftX = size.width * 0.19f
            val headX = size.width * 0.48f
            val rightX = size.width * 0.82f
            val headTop = size.height * 0.20f
            val headBottom = size.height * 0.80f
            drawLine(color, Offset(leftX, centerY), Offset(rightX, centerY), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(leftX, centerY), Offset(headX, headTop), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(leftX, centerY), Offset(headX, headBottom), stroke, cap = StrokeCap.Round)
        }
    }
}
