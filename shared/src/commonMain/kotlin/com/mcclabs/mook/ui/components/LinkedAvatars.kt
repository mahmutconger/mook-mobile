package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.ic_user
import org.jetbrains.compose.resources.painterResource

@Composable
fun LinkedAvatars(
    userPhotoUrl: String?,
    matchedPhotoUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(
                photoUrl = userPhotoUrl,
                contentDescription = "Your photo",
                modifier = Modifier.zIndex(1f)
            )

            AvatarCircle(
                photoUrl = matchedPhotoUrl,
                contentDescription = "Your match's photo",
                modifier = Modifier
                    .offset(x = (-20).dp)
                    .zIndex(0f)
            )
        }
        
        Icon(
            imageVector = Icons.Default.Favorite, // glowing cyan icon in the middle
            contentDescription = null,
            tint = NeonColors.Primary,
            modifier = Modifier
                .offset(x = (-10).dp)
                .zIndex(2f)
        )
    }
}

/** One neon-ringed avatar; falls back to a person icon when there is no photo. */
@Composable
private fun AvatarCircle(
    photoUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(NeonColors.Card)
            .border(2.dp, NeonColors.Primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(Res.drawable.ic_user),
                contentDescription = contentDescription,
                tint = NeonColors.TextTertiary,
                modifier = Modifier.size(48.dp)
            )
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
