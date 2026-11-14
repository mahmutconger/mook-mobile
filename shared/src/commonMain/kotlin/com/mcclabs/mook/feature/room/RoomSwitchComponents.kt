package com.mcclabs.mook.feature.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.LocalAppColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.room_access_hint
import mook.shared.generated.resources.room_access_open_rooms
import mook.shared.generated.resources.room_access_open_rooms_unlimited
import mook.shared.generated.resources.room_access_out_of_switches
import mook.shared.generated.resources.room_access_switches_left
import mook.shared.generated.resources.room_access_switches_unlimited
import mook.shared.generated.resources.room_access_title
import mook.shared.generated.resources.room_access_upgrade
import mook.shared.generated.resources.room_confirm_body
import mook.shared.generated.resources.room_confirm_body_unknown
import mook.shared.generated.resources.room_confirm_cancel
import mook.shared.generated.resources.room_confirm_evict
import mook.shared.generated.resources.room_confirm_title
import mook.shared.generated.resources.room_confirm_yes
import mook.shared.generated.resources.room_preparing_body
import mook.shared.generated.resources.room_preparing_title
import mook.shared.generated.resources.room_status_active
import mook.shared.generated.resources.room_status_locked
import mook.shared.generated.resources.room_status_open
import org.jetbrains.compose.resources.stringResource

/** Oda kartının altındaki küçük erişim rozeti: Aktif / Açık / Kilitli. */
@Composable
internal fun RoomStatusChip(status: RoomCardStatus, onGradient: Boolean) {
    val (icon, label) = when (status) {
        RoomCardStatus.ACTIVE -> Icons.Rounded.CheckCircle to Res.string.room_status_active
        RoomCardStatus.OPEN -> Icons.Rounded.LockOpen to Res.string.room_status_open
        RoomCardStatus.LOCKED -> Icons.Rounded.Lock to Res.string.room_status_locked
    }
    val content = when {
        onGradient -> LocalAppColors.current.onGradient
        status == RoomCardStatus.LOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val container = when {
        onGradient -> LocalAppColors.current.onGradient.copy(alpha = 0.18f)
        status == RoomCardStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Seçicinin üstündeki özet: kaç oda açık, bugün kaç hak kaldı ve kuralın kısa açıklaması.
 * Hak bittiyse "Planı yükselt" düğmesi doğrudan Paywall'a götürür.
 */
@Composable
internal fun RoomAccessCard(state: RoomSwitchUiState, onUpgrade: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.room_access_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        val openCount = state.openRoomCodes.size
        Text(
            text = state.roomSlots
                ?.let { stringResource(Res.string.room_access_open_rooms, openCount, it) }
                ?: stringResource(Res.string.room_access_open_rooms_unlimited, openCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            state.hasUnlimitedSwitches -> Text(
                text = stringResource(Res.string.room_access_switches_unlimited),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.switchesRemaining != null -> Text(
                text = stringResource(Res.string.room_access_switches_left, state.switchesRemaining),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.switchesRemaining == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(Res.string.room_access_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.switchesRemaining == 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.room_access_out_of_switches),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(Res.string.room_access_upgrade), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "X oda değiştirme hakkın var, kullanmak istediğinden emin misin?" onayı. */
@Composable
internal fun RoomSwitchConfirmDialog(
    prompt: RoomSwitchConfirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        icon = { Text(prompt.target.flagEmoji, style = MaterialTheme.typography.headlineMedium) },
        title = {
            Text(
                text = stringResource(Res.string.room_confirm_title, prompt.target.name),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = prompt.switchesRemaining
                        ?.let { stringResource(Res.string.room_confirm_body, it) }
                        ?: stringResource(Res.string.room_confirm_body_unknown),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                prompt.roomToClose?.let { closing ->
                    Text(
                        text = stringResource(Res.string.room_confirm_evict, "${closing.flagEmoji} ${closing.name}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(Res.string.room_confirm_yes), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.room_confirm_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * Oda değişirken tüm ekranı kaplayan "Oda hazırlanıyor" ilerleme ekranı. Dokunuşları yutar
 * (aynı anda ikinci bir oda seçilemez). Çubuk, sunucu yanıtı gelene kadar %90'a doğru yavaşça
 * ilerler; geçiş bitince ekran kapanır.
 */
@Composable
internal fun RoomPreparingOverlay(language: Language) {
    val progress = remember(language.code) { Animatable(0f) }
    LaunchedEffect(language.code) {
        progress.animateTo(0.9f, animationSpec = tween(durationMillis = 4_000, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Text(language.flagEmoji, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.room_preparing_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.room_preparing_body, language.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
