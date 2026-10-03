package com.mcclabs.mook.feature.update

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.update_now
import mook.shared.generated.resources.update_required_body
import mook.shared.generated.resources.update_required_title
import org.jetbrains.compose.resources.stringResource

/**
 * Zorla Güncelleme: `min_required_version`in altındaki bir istemci için kalıcı,
 * kapatılamaz tam ekran — [com.mcclabs.mook.feature.moderation.AccountSuspendedScreen]
 * ile AYNI desen. `App.kt`, [com.mcclabs.mook.domain.model.UpdateState.ForceUpdateRequired]
 * sürdüğü SÜRECE `AppNavGraph()`'ı HİÇ compose ETMEZ; "geri" tuşuyla ya da başka bir
 * gezinme yoluyla bu ekrandan kaçılamamasının EN güçlü garantisi budur — yeni Firestore
 * kuralları/alanları bekleyen eski bir istemcinin sunucuyla konuşmaya DEVAM edip veri
 * bozulmasına yol açmasını engeller.
 *
 * [onUpdateNowClicked] başarısız olsa BİLE (ör. Play Store yüklü değil) bu ekran
 * kapanmaz — kullanıcı yalnızca butona tekrar dokunabilir; ASLA bir "Daha sonra"/kapat
 * seçeneği YOKTUR, çünkü bu ekranın TÜM amacı atlanamaz olmaktır.
 */
@Composable
fun ForceUpdateScreen(onUpdateNowClicked: () -> Unit) {
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
                text = stringResource(Res.string.update_required_title),
                style = MaterialTheme.typography.headlineMedium,
                color = NeonColors.Primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.update_required_body),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            NeonPrimaryButton(
                text = stringResource(Res.string.update_now),
                onClick = onUpdateNowClicked,
            )
        }
    }
}
