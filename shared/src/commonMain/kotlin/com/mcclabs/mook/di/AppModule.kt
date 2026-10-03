package com.mcclabs.mook.di

import com.mcclabs.mook.data.local.createLocalKeyValueStore
import com.mcclabs.mook.data.billing.PlanCatalogRepositoryImpl
import com.mcclabs.mook.domain.billing.PlanCatalogRepository
import com.mcclabs.mook.ui.components.LimitSheetViewModel
import com.mcclabs.mook.domain.billing.TrialOfferUseCase
import com.mcclabs.mook.feature.likedme.LikedMeViewModel
import com.mcclabs.mook.domain.repository.LikedMeRepository
import com.mcclabs.mook.data.repository.LikedMeRepositoryImpl
import com.mcclabs.mook.domain.billing.ProfileVisitInterstitialUseCase
import com.mcclabs.mook.domain.billing.UpsellAwareInterstitialGateway
import com.mcclabs.mook.domain.billing.DailyUpsellCoordinator
import com.mcclabs.mook.data.ads.createAdCounterLocalDataSource
import com.mcclabs.mook.data.ads.AdCounterRepositoryImpl
import com.mcclabs.mook.domain.billing.AdCounterRepository
import com.mcclabs.mook.data.repository.AdConfigRepositoryImpl
import com.mcclabs.mook.domain.billing.SubscriptionChangeTracker
import com.mcclabs.mook.domain.billing.AdConfigRepository
import com.mcclabs.mook.feature.consent.PrivacyConsentViewModel
import com.mcclabs.mook.domain.auth.LogoutUseCase
import com.mcclabs.mook.data.repository.AuthRepositoryImpl
import com.mcclabs.mook.data.repository.DiscoverRepositoryImpl
import com.mcclabs.mook.data.repository.LanguageRepositoryImpl
import com.mcclabs.mook.data.repository.SettingsRepositoryImpl
import com.mcclabs.mook.domain.repository.AuthRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.LanguageRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
import com.mcclabs.mook.data.repository.InteractionRepositoryImpl
import com.mcclabs.mook.domain.repository.InteractionRepository
import com.mcclabs.mook.data.repository.ChatRepositoryImpl
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.data.repository.PushTokenRepositoryImpl
import com.mcclabs.mook.data.repository.MookProfileRepositoryImpl
import com.mcclabs.mook.data.repository.UserTimeZoneRepositoryImpl
import com.mcclabs.mook.domain.repository.MookProfileRepository
import com.mcclabs.mook.domain.repository.UserTimeZoneRepository
import com.mcclabs.mook.domain.repository.PushTokenRepository
import com.mcclabs.mook.data.repository.RoomSlotRepositoryImpl
import com.mcclabs.mook.domain.repository.RoomSlotRepository
import com.mcclabs.mook.domain.billing.TierDowngradeUseCase
import com.mcclabs.mook.feature.chat.ChatViewModel
import com.mcclabs.mook.feature.chatlist.ChatListViewModel
import com.mcclabs.mook.feature.login.LoginViewModel
import com.mcclabs.mook.feature.onboarding.OnboardingViewModel
import com.mcclabs.mook.feature.registration.RegistrationViewModel
import com.mcclabs.mook.feature.discover.DiscoverViewModel
import com.mcclabs.mook.feature.eula.EulaGateViewModel
import com.mcclabs.mook.feature.room.RoomGateViewModel
import com.mcclabs.mook.feature.room.RoomSwitchViewModel
import com.mcclabs.mook.feature.profile.ProfileDetailsViewModel
import com.mcclabs.mook.feature.filters.FiltersViewModel
import com.mcclabs.mook.feature.liked.LikedViewModel
import com.mcclabs.mook.feature.match.MatchViewModel
import com.mcclabs.mook.feature.settings.SettingsViewModel
import com.mcclabs.mook.feature.paywall.PaywallViewModel
import com.mcclabs.mook.data.repository.UpdateRepositoryImpl
import com.mcclabs.mook.data.repository.RemoteConfigRepositoryImpl
import com.mcclabs.mook.data.analytics.AnalyticsRepositoryImpl
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.repository.RemoteConfigRepository
import com.mcclabs.mook.data.repository.DataExportRepositoryImpl
import com.mcclabs.mook.domain.privacy.DataExportRepository
import com.mcclabs.mook.domain.privacy.ExportUserDataUseCase
import com.mcclabs.mook.data.repository.CustomerSupportRepositoryImpl
import com.mcclabs.mook.domain.support.CustomerSupportRepository
import com.mcclabs.mook.domain.support.CustomerSupportUseCase
import com.mcclabs.mook.feature.update.UpdateViewModel
import com.mcclabs.mook.domain.update.ForceUpdateUseCase
import com.mcclabs.mook.domain.update.InAppUpdateGateway
import com.mcclabs.mook.domain.update.createInAppUpdateGateway
import com.mcclabs.mook.data.billing.TierAwarePremiumRepository
import com.mcclabs.mook.domain.billing.PremiumRepository
import com.mcclabs.mook.data.billing.createPlatformSubscriptionRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.data.connectivity.createConnectivityObserver
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.data.billing.createPendingActionQueue
import com.mcclabs.mook.domain.billing.PendingActionQueue
import com.mcclabs.mook.domain.billing.SwipeTimeoutFallbackHandler
import com.mcclabs.mook.data.consent.createConsentRepository
import com.mcclabs.mook.domain.consent.ConsentRepository
import com.mcclabs.mook.domain.analytics.AnalyticsParameterSanitizer
import com.mcclabs.mook.domain.analytics.DefaultSensitiveAnalyticsKeyPolicy
import com.mcclabs.mook.domain.analytics.SensitiveAnalyticsKeyPolicy
import com.mcclabs.mook.domain.billing.RecoverPendingSwipeActionsUseCase
import com.mcclabs.mook.ads.createLikeInterstitialGateway
import com.mcclabs.mook.domain.billing.LikeInterstitialGateway
import com.mcclabs.mook.feature.sso.SsoAuthorizeViewModel
import com.mcclabs.mook.feature.profile.edit.EditProfileViewModel
import com.mcclabs.mook.data.translation.FirebaseFunctionsTranslator
import com.mcclabs.mook.domain.translation.Translator
import com.mcclabs.mook.feature.walktalkdemo.WalkTalkDemoViewModel
import com.mcclabs.mook.data.sso.FirebaseSsoAuthRepository
import com.mcclabs.mook.data.sso.StaticSsoClientRegistry
import com.mcclabs.mook.data.sso.createAuthStateStore
import com.mcclabs.mook.data.sso.platformSecureRandomBytes
import com.mcclabs.mook.domain.sso.AuthStateStore
import com.mcclabs.mook.domain.sso.AuthStateValidator
import com.mcclabs.mook.domain.sso.EpochClock
import com.mcclabs.mook.domain.sso.GenerateAuthStateUseCase
import com.mcclabs.mook.domain.sso.SecureRandomSource
import com.mcclabs.mook.domain.sso.SsoAuthRepository
import com.mcclabs.mook.domain.sso.SsoClientRegistry
import com.mcclabs.mook.domain.sso.VerifyAuthCallbackUseCase
import com.mcclabs.mook.domain.sso.WhitelistCallbackValidator
import com.mcclabs.mook.util.getCurrentTimeMillis
import org.koin.core.module.dsl.factoryOf
import com.mcclabs.mook.domain.account.DeleteAccountUseCase
import com.mcclabs.mook.domain.account.AccountMergeUseCase
import com.mcclabs.mook.data.repository.ModerationRepositoryImpl
import com.mcclabs.mook.domain.repository.ModerationRepository
import com.mcclabs.mook.domain.moderation.UserModerationUseCase
import com.mcclabs.mook.domain.billing.RestoreSubscriptionUseCase
import com.mcclabs.mook.domain.billing.PriceChangeConfirmationUseCase
import com.mcclabs.mook.domain.billing.WinBackOfferUseCase
import com.mcclabs.mook.domain.billing.BoostManagerUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    singleOf(::LanguageRepositoryImpl) bind LanguageRepository::class
    singleOf(::DiscoverRepositoryImpl) bind DiscoverRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
    singleOf(::InteractionRepositoryImpl) bind InteractionRepository::class
    singleOf(::UpdateRepositoryImpl) bind com.mcclabs.mook.domain.repository.UpdateRepository::class
    single<InAppUpdateGateway> { createInAppUpdateGateway() }
    factoryOf(::ForceUpdateUseCase)
    singleOf(::RemoteConfigRepositoryImpl) bind RemoteConfigRepository::class
    // Gereksinim 4 (Faz 6): Analytics'e giden HER olay parametre haritasını hassas
    // alanlara karşı elemeden geçirir -- bkz. [AnalyticsParameterSanitizer] KDoc'u.
    single<SensitiveAnalyticsKeyPolicy> { DefaultSensitiveAnalyticsKeyPolicy() }
    singleOf(::AnalyticsParameterSanitizer)
    singleOf(::AnalyticsRepositoryImpl) bind AnalyticsRepository::class
    // Android receives the RevenueCat implementation; iOS stays a no-op until iOS commerce
    // is deliberately launched. Older premium-only gates observe only the true Premium tier.
    // Plan sınırlarının tek doğruluk kaynağı: Firestore `config/plans` + yerel önbellek.
    single<PlanCatalogRepository> { PlanCatalogRepositoryImpl(createLocalKeyValueStore("mook_plan_catalog")) }
    single<SubscriptionRepository> { createPlatformSubscriptionRepository(get()) }
    single<PremiumRepository> { TierAwarePremiumRepository(get()) }
    // Gereksinim 1.2: tüm ağ gerektiren eylemler bu tekil gözlemciyi paylaşır.
    single<ConnectivityObserver> { createConnectivityObserver() }
    // Gereksinim 1.3: reklam öncesi kuyruğa alınan beğeni eylemleri için kalıcı depo.
    single<PendingActionQueue> { createPendingActionQueue() }
    // Gereksinim 1 (Faz 6): `swipe` çağrısı için zaman aşımı + kalıcı kuyruk düşme kararı --
    // bkz. [SwipeTimeoutFallbackHandler] KDoc'u.
    single { SwipeTimeoutFallbackHandler(get(), get()) }
    // Gereksinim 3 (Faz 6, KVKK/GDPR): UMP + Firebase Consent Mode v2 + KVKK'ye özgü
    // sınır ötesi aktarım onayının TEK doğruluk kaynağı -- bkz. [ConsentRepository] KDoc'u.
    single<ConsentRepository> { createConsentRepository() }

    // Gereksinim 5 (Faz 6, KVKK Madde 11): veri dışa aktarma isteği.
    singleOf(::DataExportRepositoryImpl) bind DataExportRepository::class
    single { ExportUserDataUseCase(get()) }
    // Gereksinim 7 (Faz 6): destek/yönetici aracı -- bkz. [CustomerSupportUseCase] KDoc'u.
    singleOf(::CustomerSupportRepositoryImpl) bind CustomerSupportRepository::class
    single { CustomerSupportUseCase(get()) }
    factoryOf(::RecoverPendingSwipeActionsUseCase)
    // Gereksinim 2.15: AdMob SDK'sına bağımlı interstitial sağlayıcı — bkz. [LikeInterstitialGateway] KDoc'u.
    // Remote Config: reklam kill-switch'i ve sıklık ayarları (bkz. [AdRemoteConfig]).
    single<AdConfigRepository> { AdConfigRepositoryImpl() }
    single { SubscriptionChangeTracker(get(), get()) }
    // Reklam sayaçları (profil ziyareti, günlük upsell zamanı) — yerel, kullanıcıya göre ayrışık.
    single<AdCounterRepository> { AdCounterRepositoryImpl(createAdCounterLocalDataSource()) }
    single { DailyUpsellCoordinator(get()) }
    // Her geçiş reklamı kapandıktan sonra günlük upsell kartını tetikleyen dekoratör.
    single<LikeInterstitialGateway> {
        UpsellAwareInterstitialGateway(createLikeInterstitialGateway(get(), get()), get())
    }
    factory { ProfileVisitInterstitialUseCase(get(), get()) }
    singleOf(::LikedMeRepositoryImpl) bind LikedMeRepository::class
    viewModelOf(::LikedMeViewModel)
    // Limit sayfası: "Standart'ı ücretsiz dene" uygunluğu (bkz. TrialOfferUseCase).
    factory { TrialOfferUseCase(get()) }
    viewModelOf(::LimitSheetViewModel)
    // Gereksinim 1.7: açık oda slotları + kademe düşüşünde fazla odaları kapatma kararı.
    singleOf(::RoomSlotRepositoryImpl) bind RoomSlotRepository::class
    factoryOf(::TierDowngradeUseCase)

    // Hesap silme iş kuralı (Google Play uyumu) — durumsuz use case.
    factoryOf(::DeleteAccountUseCase)

    // Gereksinim 1.9: yeni bir SSO kimliğiyle giriş yapılırken cihazdaki mevcut RevenueCat
    // kimliğiyle çakışma olup olmadığını tespit eden, durumsuz use case.
    factoryOf(::AccountMergeUseCase)

    // Gereksinim 1.13: sunucunun uyguladığı yasaklama durumunu (`isBanned`) canlı izleyen
    // TEKİL depo — uygulama boyunca tek bir dinleyici yaşamalı, her enjeksiyonda yeniden
    // kurulmamalıdır (bkz. `ModerationRepositoryImpl` KDoc'u).
    singleOf(::ModerationRepositoryImpl) bind ModerationRepository::class
    // Katı çıkış sırası (FCM → RevenueCat → signOut). `stepTimeoutMillis` varsayılan değerli
    // olduğu için Koin'in kurucu referansı (factoryOf) yerine açık tanım kullanılır.
    factory { LogoutUseCase(get(), get(), get()) }
    factoryOf(::UserModerationUseCase)

    // Gereksinim 2.1: geri yükleme akışını iki ViewModel için tek bir yerden yöneten use case.
    factoryOf(::RestoreSubscriptionUseCase)
    factoryOf(::PriceChangeConfirmationUseCase)
    factoryOf(::WinBackOfferUseCase)

    // Gereksinim 2.12 (Faz 4): Boost'un takvim ayı değil FATURALANDIRMA DÖNGÜSÜ bazlı
    // yaşam döngüsünü yöneten, durumsuz use case.
    factoryOf(::BoostManagerUseCase)

    // Translation for the WalkTalk demo. The DeepL key lives in the Cloud Function's
    // server config, never here: swapping this line for a Ktor-backed Translator is the
    // only change needed to move off callables.
    single<Translator> { FirebaseFunctionsTranslator() }

    // SSO güvenliği: beyaz liste, state üretimi/doğrulaması ve token üretimi.
    single<SsoClientRegistry> { StaticSsoClientRegistry() }
    single<AuthStateStore> { createAuthStateStore() }
    single<SecureRandomSource> { SecureRandomSource(::platformSecureRandomBytes) }
    single<EpochClock> { EpochClock(::getCurrentTimeMillis) }
    singleOf(::WhitelistCallbackValidator)
    single { AuthStateValidator(get()) }
    factoryOf(::GenerateAuthStateUseCase)
    factoryOf(::VerifyAuthCallbackUseCase)
    singleOf(::FirebaseSsoAuthRepository) bind SsoAuthRepository::class

    viewModelOf(::OnboardingViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegistrationViewModel)
    viewModelOf(::DiscoverViewModel)
    viewModelOf(::FiltersViewModel)
    viewModelOf(::LikedViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PrivacyConsentViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::EulaGateViewModel)
    viewModelOf(::RoomGateViewModel)
    viewModelOf(::RoomSwitchViewModel)
    viewModelOf(::SsoAuthorizeViewModel)
    viewModelOf(::EditProfileViewModel)
    viewModelOf(::WalkTalkDemoViewModel)
    viewModelOf(::ChatListViewModel)
    viewModelOf(::PaywallViewModel)

    singleOf(::ChatRepositoryImpl) bind ChatRepository::class
    singleOf(::PushTokenRepositoryImpl) bind PushTokenRepository::class
    singleOf(::MookProfileRepositoryImpl) bind MookProfileRepository::class
    // Varsayılan parametreli kurucu: `singleOf` yerine açık lambda kullanılır.
    single<UserTimeZoneRepository> { UserTimeZoneRepositoryImpl() }

    // These take a screen argument alongside their injected dependencies.
    viewModel { parameters -> ProfileDetailsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), parameters.get()) }
    viewModel { parameters -> MatchViewModel(get(), get(), parameters.get()) }
    viewModel { parameters -> ChatViewModel(get(), get(), get(), get(), get(), parameters.get(), parameters.get()) }
}
