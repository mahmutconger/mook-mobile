package com.mcclabs.mook.domain.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * [com.mcclabs.mook.ads.AdMobActivityHolder]/`RevenueCatActivityHolder` ile AYNI desen:
 * Play Core'un "Immediate" akışı (`startUpdateFlowForResult`) bir Activity VE önceden
 * kayıtlı bir `ActivityResultLauncher` gerektirir — ikisi de yalnızca `MainActivity`
 * ön plandayken geçerlidir.
 */
object InAppUpdateActivityHolder {
    @Volatile
    var activity: ComponentActivity? = null

    @Volatile
    var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
}
