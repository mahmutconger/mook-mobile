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
import com.mcclabs.mook.feature.login.LoginViewModel
import com.mcclabs.mook.feature.onboarding.OnboardingViewModel
import com.mcclabs.mook.feature.registration.RegistrationViewModel
import com.mcclabs.mook.feature.discover.DiscoverViewModel
import com.mcclabs.mook.feature.eula.EulaGateViewModel
import com.mcclabs.mook.feature.profile.ProfileDetailsViewModel
import com.mcclabs.mook.feature.filters.FiltersViewModel
import com.mcclabs.mook.feature.liked.LikedViewModel
import com.mcclabs.mook.feature.match.MatchViewModel
import com.mcclabs.mook.feature.settings.SettingsViewModel
import com.mcclabs.mook.data.repository.UpdateRepositoryImpl
import com.mcclabs.mook.feature.update.UpdateViewModel
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

    viewModelOf(::OnboardingViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegistrationViewModel)
    viewModelOf(::DiscoverViewModel)
    viewModelOf(::FiltersViewModel)
    viewModelOf(::LikedViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::EulaGateViewModel)

    // These take a screen argument alongside their injected dependencies.
    viewModel { parameters -> ProfileDetailsViewModel(get(), parameters.get()) }
    viewModel { parameters -> MatchViewModel(get(), parameters.get()) }
}
