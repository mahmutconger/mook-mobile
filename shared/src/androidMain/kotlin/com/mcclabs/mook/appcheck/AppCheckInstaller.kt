package com.mcclabs.mook.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): Play Integrity tabanlı App Check
 * sağlayıcısının kurulumunu yapan TEK nokta.
 *
 * `MookApplication.onCreate()`de, BAŞKA HİÇBİR Firebase ağ çağrısından (RevenueCat
 * yapılandırması dahil) ÖNCE çağrılmalıdır: SDK bu çağrıdan SONRAKİ TÜM Firestore/Cloud
 * Functions isteklerine otomatik olarak bir App Check jetonu ekler; kurulumdan ÖNCE giden
 * bir istek jetonsuz gider ve artık `enforceAppCheck: true` olan callable'lar
 * (`functions/src/monetization.ts`, `discoverFeed.ts`, `moderation.ts`, `deleteAccount.ts`)
 * için İLK çağrının reddedilmesine yol açabilir.
 */
object AppCheckInstaller {
    /** `MookApplication.onCreate()`den, uygulama ömrü boyunca yalnızca BİR KEZ çağrılır. */
    fun installIfNeeded() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}
