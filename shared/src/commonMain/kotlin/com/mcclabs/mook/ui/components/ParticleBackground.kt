package com.mcclabs.mook.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun ParticleBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val particles = remember {
        List(30) {
            Particle(
                xProgress = Random.nextFloat(),
                yProgress = Random.nextFloat(),
                sizeDp = Random.nextDouble(2.0, 8.0).toFloat(),
                speedMultiplier = Random.nextFloat() * 0.5f + 0.5f
            )
        }
    }

    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            // Parçacıklar yukarı doğru hareket eder.
            val yOffset = (particle.yProgress - progress * particle.speedMultiplier) % 1f
            val y = if (yOffset < 0f) yOffset + 1f else yOffset
            val x = particle.xProgress * size.width
            val yPos = y * size.height

            drawCircle(
                color = color,
                radius = particle.sizeDp.dp.toPx(),
                center = Offset(x = x, y = yPos)
            )
        }
    }
}

private data class Particle(
    val xProgress: Float,
    val yProgress: Float,
    val sizeDp: Float,
    val speedMultiplier: Float
)
