package com.mcclabs.mook.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mcclabs.mook.domain.billing.MidnightCountdown
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.util.getCurrentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.limit_sheet_resets_in
import org.jetbrains.compose.resources.stringResource

/**
 * Yerel gece yarısına kadar saniye saniye geri sayan metin ("Hakların 05:07:09 sonra yenilenir").
 *
 * Ana iş parçacığını BLOKLAMAZ: döngü `delay` ile askıya alınır ve bir sonraki tam saniyeye
 * hizalanır; hesaplama yalnızca birkaç tarih işlemidir. Bileşen ekrandan çıkınca döngü iptal olur.
 */
@Composable
fun MidnightCountdownText(modifier: Modifier = Modifier) {
    val timeZone = TimeZone.currentSystemDefault()
    val remaining by produceState(initialValue = MidnightCountdown.millisUntilNextMidnight(getCurrentTimeMillis(), timeZone)) {
        while (true) {
            val now = getCurrentTimeMillis()
            value = MidnightCountdown.millisUntilNextMidnight(now, timeZone)
            delay(1_000L - now % 1_000L)
        }
    }
    Text(
        text = stringResource(Res.string.limit_sheet_resets_in, MidnightCountdown.format(remaining)),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = NeonColors.TextPrimary,
        modifier = modifier,
    )
}
