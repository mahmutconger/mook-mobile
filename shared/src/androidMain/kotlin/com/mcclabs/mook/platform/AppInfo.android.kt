package com.mcclabs.mook.platform

/**
 * Yüklü APK'nın GERÇEK `versionName`'ini [AppContextHolder] üzerinden okur.
 *
 * Önceki implementasyon `"1.0.0"` döndüren sabit kodlanmış bir yer tutucuydu — bu,
 * zorla güncelleme kontrolünü (bkz. [com.mcclabs.mook.domain.update.ForceUpdateUseCase])
 * anlamsız kılıyordu, çünkü karşılaştırma her zaman aynı sahte sürümle yapılıyordu.
 */
actual fun getAppVersion(): String {
    val context = AppContextHolder.applicationContext ?: return "0.0.0"
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "0.0.0"
    } catch (e: Exception) {
        // Bu okuma ASLA çökmemeli — başarısız olursa "0.0.0" güvenli tarafta kalır
        // (karşılaştırma her zaman güncelleme GEREKLİ gösterir, ASLA yanlışlıkla atlamaz).
        "0.0.0"
    }
}

/**
 * Returns the platform-specific URL for the application's store page.
 */
actual fun getStoreUrl(): String {
    return "https://play.google.com/store/apps/details?id=com.mcclabs.mook"
}
