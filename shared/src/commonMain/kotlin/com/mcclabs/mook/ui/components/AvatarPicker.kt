package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.add_photo_svgrepo_com
import mook.shared.generated.resources.ic_user
import mook.shared.generated.resources.avatar_pick_cd
import mook.shared.generated.resources.avatar_default_cd
import mook.shared.generated.resources.avatar_selected_cd
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Circular avatar picker with a placeholder icon and an "add" overlay button.
 *
 * When [avatarUri] is `null` a generic person icon is shown on a
 * [NeonColors.Card] background. A small cyan "+" button is positioned
 * at the bottom-right corner to initiate avatar selection.
 *
 * @param avatarUri   URI of the selected avatar image, or `null` for default.
 * @param onPickAvatar Callback invoked when the user taps to pick an avatar.
 * @param modifier     Optional [Modifier].
 */
@Composable
fun AvatarPicker(
    avatarUri: String?,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Main circular avatar area
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(NeonColors.Card)
                .clickable(onClick = onPickAvatar),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUri.isNullOrBlank()) {
                // Default person placeholder
                Icon(
                    painter = painterResource(Res.drawable.ic_user),
                    contentDescription = stringResource(Res.string.avatar_default_cd),
                    tint = NeonColors.TextTertiary,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = stringResource(Res.string.avatar_selected_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Small "+" overlay button at bottom-right
        Box(
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp)
                .clip(CircleShape)
                .background(NeonColors.Primary)
                .clickable(onClick = onPickAvatar),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.add_photo_svgrepo_com),
                contentDescription = stringResource(Res.string.avatar_pick_cd),
                tint = NeonColors.Background,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
