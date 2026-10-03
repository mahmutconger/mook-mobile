package com.mcclabs.mook

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.mcclabs.mook.appcheck.AppCheckInstaller
import com.mcclabs.mook.platform.AppContextHolder

/**
 * Initializes the Android-only RevenueCat SDK before Compose or authentication start.
 *
 * Kimlik BAĞLAMA (logIn) burada YAPILMAZ (Gereksinim 1.9) — yalnızca çıkışta (logOut)
 * koşulsuz bir güvenlik ağı sağlar. Giriş kimliği, `App.kt`'deki `signedInUid` efektinde,
 * önce çakışma kontrolünden (`AccountMergeUseCase`) geçirilerek bağlanır.
 */
class MookApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): BAŞKA HİÇBİR Firebase
        // ağ çağrısından (RevenueCat yapılandırması dahil) ÖNCE çağrılır -- bkz.
        // AppCheckInstaller KDoc'u.
        AppCheckInstaller.installIfNeeded()

        // Gereksinim (Zorla Güncelleme): [getAppVersion]'ın (bkz. AppInfo.android.kt) yüklü
        // APK'nın GERÇEK versionName'ini PackageManager üzerinden okuyabilmesi için — herhangi
        // bir Activity resume olmadan, uygulama başlar başlamaz kullanılabilir olmalıdır.
        AppContextHolder.applicationContext = applicationContext

        if (!Purchases.isConfigured) {
            Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(
                PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_PUBLIC_SDK_KEY).build(),
            )
        }
        // Gereksinim 2.1: "Restore Behavior" bir SDK yapılandırma seçeneği DEĞİLDİR — yalnızca
        // RevenueCat panelinden (Project Settings → Restore Behavior) ayarlanabilir. Bu proje
        // için orada "Transfer if there are no active subscriptions" seçilmiş OLMALIDIR: bu,
        // aynı Google hesabıyla farklı bir WalkMatch hesabına geçen bir kullanıcının, önceki
        // hesapta hâlâ aktif bir abonelik yoksa aboneliğini yeni hesaba taşımasına izin verir;
        // aktif bir abonelik VARSA aktarımı REDDEDER (bkz. `RestoreSubscriptionUseCase` ve
        // `BillingError.SubscriptionLinkedToAnotherAccount`).
        

        // Gereksinim 1.9: `logIn()` dalı BİLEREK burada YOKTUR. Firebase'in `currentUser.uid`
        // değeri, oturum açma/kayıt tamamlanır tamamlanmaz SDK içinde eş zamanlı değiştiğinden,
        // bu global (Application seviyesi) dinleyici koşulsuz bir `logIn()` çağırsaydı, her
        // zaman App.kt'deki çakışma kontrolünün (bkz. AccountMergeUseCase) ÖNÜNE geçer ve onu
        // anlamsız kılardı. Bu yüzden kimlik BAĞLAMA (logIn) sorumluluğu artık YALNIZCA
        // App.kt'deki `signedInUid` efektindedir — orada önce AccountMergeUseCase.detectConflict()
        // çalışır, çakışma yoksa/kullanıcı onaylarsa ancak O ZAMAN logIn() çağrılır.
        // Çıkış (logOut) tarafında ise devredilecek bir abonelik riski YOKTUR — bu yüzden bu
        // dal güvenle burada, koşulsuz kalabilir.
        // Güvenlik ağı: normal çıkış `LogoutUseCase` içinde RevenueCat'i signOut'tan ÖNCE zaten
        // ayırır; bu dinleyici yalnızca o akışı atlayan oturum kapanışlarını (ör. hesap silme
        // sonrası) yakalar. Kimlik zaten anonimse çağrı atlanır — aksi halde RevenueCat her
        // normal çıkıştan sonra gereksiz bir "anonim kullanıcı" hatası loglardı.
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser == null && Purchases.isConfigured && !Purchases.sharedInstance.isAnonymous) {
                Purchases.sharedInstance.logOut()
            }
        }
    }
}
