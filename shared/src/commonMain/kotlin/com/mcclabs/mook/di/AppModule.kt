package com.mcclabs.mook.di

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
import com.mcclabs.mook.domain.repository.PushTokenRepository
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
import com.mcclabs.mook.feature.update.UpdateViewModel
import com.mcclabs.mook.data.billing.TierAwarePremiumRepository
import com.mcclabs.mook.domain.billing.PremiumRepository
import com.mcclabs.mook.data.billing.createPlatformSubscriptionRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository
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
    // Android receives the RevenueCat implementation; iOS stays a no-op until iOS commerce
    // is deliberately launched. Older premium-only gates observe only the true Premium tier.
    single<SubscriptionRepository> { createPlatformSubscriptionRepository() }
    single<PremiumRepository> { TierAwarePremiumRepository(get()) }

    // Hesap silme iş kuralı (Google Play uyumu) — durumsuz use case.
    factoryOf(::DeleteAccountUseCase)

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

    // These take a screen argument alongside their injected dependencies.
    viewModel { parameters -> ProfileDetailsViewModel(get(), get(), get(), get(), parameters.get()) }
    viewModel { parameters -> MatchViewModel(get(), get(), parameters.get()) }
    viewModel { parameters -> ChatViewModel(get(), get(), parameters.get(), parameters.get()) }
}
