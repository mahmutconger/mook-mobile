package com.mcclabs.mook.platform

import android.content.Context

/**
 * `MookApplication.onCreate()`'te bir kez ayarlanan, uygulama ömrü boyunca güvenle
 * tutulabilen (Application Context sızıntı yaratmaz) global bir bağlam kaynağı.
 *
 * Bu, [com.mcclabs.mook.ads.AdMobActivityHolder]/`RevenueCatActivityHolder`'dan
 * KASITLI olarak FARKLIDIR: onlar bir Activity gerektiren (ve bu yüzden yalnızca ön
 * plandayken geçerli olan) SDK çağrıları içindir; [getAppVersion] ise uygulama daha
 * hiçbir Activity resume olmadan (ör. arka planda bir kontrol) her an güvenle
 * çağrılabilmelidir.
 */
object AppContextHolder {
    @Volatile
    var applicationContext: Context? = null
}
