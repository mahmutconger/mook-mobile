package com.mcclabs.mook.feature.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.moderation_suspended_contact_support
import mook.shared.generated.resources.moderation_suspended_message
import mook.shared.generated.resources.moderation_suspended_title
import org.jetbrains.compose.resources.stringResource

/**
 * Gereksinim 1.13: "Hesap Askıya Alındı" — kalıcı, kapatılamaz tam ekran.
 *
 * `App.kt`, kullanıcı yasaklı olduğu sürece `AppNavGraph()`'ı HİÇ compose ETMEZ; bunun
 * yerine YALNIZCA bu ekranı gösterir. Bu, "geri" tuşuyla ya da başka bir gezinme yoluyla
 * bu ekrandan kaçılamamasının EN güçlü garantisidir — çünkü geri dönülecek bir gezinme
 * yığını (back stack) yeniden compose edilmiş DURUMDA bile yoktur.
 *
 * Bir "Ayarlar" ya da "Çıkış Yap" düğmesi kasıtlı olarak YOKTUR: [UserModerationUseCase.enforceBan]
 * zaten oturumu kapatmıştır (bkz. `App.kt`'deki efekt) — bu yüzden bu ekran zaten
 * kimliği doğrulanmamış bir durumu temsil eder, kullanıcının burada yapabileceği tek şey
 * destekle iletişime geçmektir.
 */
@Composable
fun AccountSuspendedScreen() {
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NeonColors.GlassBackground)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.moderation_suspended_title),
                style = MaterialTheme.typography.headlineMedium,
                color = NeonColors.Primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.moderation_suspended_message),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            NeonPrimaryButton(
                text = stringResource(Res.string.moderation_suspended_contact_support),
                onClick = { uriHandler.openUri("mailto:destek@walktalkk.com") },
            )
        }
    }
}
