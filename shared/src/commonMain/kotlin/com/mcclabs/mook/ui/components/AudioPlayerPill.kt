package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors

@Composable
fun AudioPlayerPill(durationSec: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                color = NeonColors.GlassBackground,
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(NeonColors.Primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = NeonColors.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        val timeString = "$minutes:${seconds.toString().padStart(2, '0')}"
        
        Text(
            text = timeString,
            color = NeonColors.Primary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
