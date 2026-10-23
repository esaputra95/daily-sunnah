package id.kasirq.dailysunnah.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import id.kasirq.dailysunnah.R

enum class BottomTabIconType { HOME, QURAN, IBADAH, KAJIAN, MORE }

@Composable
fun BottomTabIcon(type: BottomTabIconType, selected: Boolean) {
    val selectionProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 600f),
        label = "tab-selection-progress",
    )
    if (type == BottomTabIconType.MORE) {
        val selectedColor = MaterialTheme.colorScheme.onPrimary
        val idleColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier.size(28.dp).graphicsLayer {
                val scale = 0.84f + (0.29f * selectionProgress)
                scaleX = scale
                scaleY = scale
                alpha = 0.74f + (0.26f * selectionProgress)
            },
        ) {
            val tile = size.minDimension * 0.28f
            val gap = size.minDimension * 0.14f
            val origin = (size.minDimension - (tile * 2f + gap)) / 2f
            val corner = CornerRadius(tile * 0.30f)
            val color = if (selected) selectedColor else idleColor
            for (row in 0..1) {
                for (column in 0..1) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(origin + column * (tile + gap), origin + row * (tile + gap)),
                        size = Size(tile, tile),
                        cornerRadius = corner,
                    )
                }
            }
        }
        return
    }

    val icon = when (type) {
        BottomTabIconType.HOME -> R.drawable.tab_home
        BottomTabIconType.QURAN -> R.drawable.tab_quran
        BottomTabIconType.IBADAH -> R.drawable.tab_ibadah
        BottomTabIconType.KAJIAN -> R.drawable.tab_kajian
        BottomTabIconType.MORE -> error("The More tab icon is drawn in Compose.")
    }
    Box(
        modifier = Modifier
            .size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    val scale = 0.84f + (0.29f * selectionProgress)
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.74f + (0.26f * selectionProgress)
                },
            contentScale = ContentScale.Fit,
        )
    }
}
