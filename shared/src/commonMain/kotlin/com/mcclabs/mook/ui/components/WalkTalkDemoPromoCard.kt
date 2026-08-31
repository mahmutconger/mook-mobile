package com.mcclabs.mook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.chat_round_line_svgrepo_com
import mook.shared.generated.resources.wtdemo_promo_body
import mook.shared.generated.resources.wtdemo_promo_cd
import mook.shared.generated.resources.wtdemo_promo_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Entry point to the live-translation demo.
 *
 * Sits at the top of the Chats tab — the moment a user is thinking about messaging is
 * the moment WalkTalk's translation is worth showing. One tap target for the whole
 * card, so it reads as a single action to a screen reader too.
 */
@Composable
fun WalkTalkDemoPromoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val description = stringResource(Res.string.wtdemo_promo_cd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NeonColors.Card)
            .border(1.dp, NeonColors.CardBorder, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.chat_round_line_svgrepo_com),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.wtdemo_promo_title),
                style = MaterialTheme.typography.titleMedium,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.wtdemo_promo_body),
                style = MaterialTheme.typography.bodySmall,
                color = NeonColors.TextSecondary,
            )
        }

        Spacer(Modifier.width(2.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = NeonColors.Primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
