package com.mcclabs.mook.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): App Check sağlayıcısının kurulumunu
 * yapan TEK nokta.
 *
 * `MookApplication.onCreate()`de, BAŞKA HİÇBİR Firebase ağ çağrısından (RevenueCat
 * yapılandırması dahil) ÖNCE çağrılmalıdır: SDK bu çağrıdan SONRAKİ TÜM Firestore/Cloud
 * Functions isteklerine otomatik olarak bir App Check jetonu ekler; kurulumdan ÖNCE giden
 * bir istek jetonsuz gider ve artık `enforceAppCheck: true` olan callable'lar
 * (`functions/src/monetization.ts`, `discoverFeed.ts`, `moderation.ts`, `deleteAccount.ts`)
 * için İLK çağrının reddedilmesine yol açabilir.
 *
 * Play Integrity yalnızca Google Play'den yüklenmiş (Play imzalı) derlemelere jeton verir.
 * Android Studio'dan kurulan debug derlemesinde jeton ALINAMAZ; istek `app: MISSING` ile
 * gider ve `switchRoom` gibi callable'lar 401 döner (ilk oda seçimi ekranında takılma).
 * Bu yüzden debug derlemesi [factory] olarak Debug sağlayıcısını verir (bkz.
 * `androidApp/src/debug/.../AppCheckProviders.kt`); onun ürettiği debug jetonunun Firebase
 * konsolunda (App Check → Uygulamalar → Debug jetonlarını yönet) kayıtlı olması gerekir.
 */
object AppCheckInstaller {
    /** `MookApplication.onCreate()`den, uygulama ömrü boyunca yalnızca BİR KEZ çağrılır. */
    fun installIfNeeded(
        factory: AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance(),
    ) {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
    }
}
