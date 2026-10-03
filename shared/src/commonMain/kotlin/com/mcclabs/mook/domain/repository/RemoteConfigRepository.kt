package com.mcclabs.mook.domain.repository

/**
 * Gereksinim 2.13 (Faz 4): Firebase Remote Config bayraklarını okumak için soyutlama.
 *
 * Diğer tüm üçüncü taraf SDK sarmalayıcıları gibi (bkz. [SubscriptionRepository],
 * [UpdateRepository]) bu arayüz, çağıran katmanların (ör. `RegistrationViewModel`)
 * doğrudan `dev.gitlive.firebase.remoteconfig` API'sine bağımlı olmasını engeller ve
 * Repository desenini korur.
 *
 * ## Güvenli varsayılan ilkesi
 * Ağ hatası, `fetchAndActivate()` başarısızlığı ya da sunucuda bayrak henüz
 * tanımlanmamışsa bu arayüzün implementasyonu HER ZAMAN `false` (özelliği KAPALI
 * kabul et) döner — daha önce `PlatformSubscriptionRepository`de kurulan
 * "belirsizlikte güvenli sonuca çök" ilkesiyle birebir tutarlıdır. Bu sayede bir
 * Remote Config okuma hatası asla kullanıcıyı istemeden Paywall'a yönlendirmez.
 */
interface RemoteConfigRepository {

    /**
     * Profil onboarding'i (kayıt sihirbazının son adımı) tamamlandığı anda kullanıcıyı
     * doğrudan — Deneme paketi ön-seçili olarak — Paywall'a yönlendirip
     * yönlendirmeyeceğimizi belirleyen `show_onboarding_trial_offer` Remote Config
     * bayrağını okur. Bayrak `true` değilse (ya da okunamıyorsa) normal
     * onboarding-sonrası akış (doğrudan ana ekrana geçiş) değişmeden devam eder.
     */
    suspend fun shouldShowOnboardingTrialOffer(): Boolean
}
