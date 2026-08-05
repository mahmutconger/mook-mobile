package com.mcclabs.mook.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.domain.model.ProficiencyLevel
import com.mcclabs.mook.ui.components.GlassmorphismCard
import com.mcclabs.mook.ui.components.LanguageDropdown
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.StepIndicator
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is OnboardingViewModel.OnboardingNavigationEvent.NavigateToLogin -> {
                    onNavigateToLogin()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Step indicator — 3 dots, first active
        StepIndicator(
            totalSteps = state.totalSteps,
            currentStep = state.currentStep,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // "STEP 1 OF 3" label
        Text(
            text = stringResource(Res.string.onboarding_step_indicator),
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 3.sp
            ),
            color = NeonColors.TextTertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Title
        Text(
            text = stringResource(Res.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            color = NeonColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = stringResource(Res.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Language selection card
        GlassmorphismCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Native Language
            Text(
                text = stringResource(Res.string.onboarding_native_language_label),
                style = MaterialTheme.typography.labelLarge,
                color = NeonColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LanguageDropdown(
                label = stringResource(Res.string.onboarding_select_native_language),
                selectedLanguage = state.selectedNativeLanguage,
                onLanguageSelected = viewModel::selectNativeLanguage,
                languages = state.availableLanguages,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Swap icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = NeonColors.Surface
                    )
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.sort_from_top_to_bottom_svgrepo_com),
                        contentDescription = stringResource(Res.string.onboarding_swap_languages_cd),
                        tint = NeonColors.Primary,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Target Language
            Text(
                text = stringResource(Res.string.onboarding_target_language_label),
                style = MaterialTheme.typography.labelLarge,
                color = NeonColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LanguageDropdown(
                label = stringResource(Res.string.onboarding_select_target_language),
                selectedLanguage = state.selectedTargetLanguage,
                onLanguageSelected = viewModel::selectTargetLanguage,
                languages = state.availableLanguages,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Target Level label + current value
            Text(
                text = stringResource(Res.string.onboarding_target_level_label),
                style = MaterialTheme.typography.labelLarge,
                color = NeonColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = state.proficiencyLevel.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = NeonColors.Primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Proficiency slider
            Slider(
                value = state.proficiencyLevel.ordinal.toFloat(),
                onValueChange = { value ->
                    val level = ProficiencyLevel.entries[value.toInt().coerceIn(0, 2)]
                    viewModel.setProficiencyLevel(level)
                },
                valueRange = 0f..2f,
                steps = 1,
                colors = SliderDefaults.colors(
                    thumbColor = NeonColors.TextPrimary,
                    activeTrackColor = NeonColors.Primary,
                    inactiveTrackColor = NeonColors.Surface,
                    activeTickColor = NeonColors.Primary,
                    inactiveTickColor = NeonColors.Surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Proficiency level labels
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProficiencyLevel.entries.forEach { level ->
                    Text(
                        text = level.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (level == state.proficiencyLevel) {
                            NeonColors.Primary
                        } else {
                            NeonColors.TextTertiary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Continue button
        NeonPrimaryButton(
            text = stringResource(Res.string.onboarding_continue_button),
            onClick = viewModel::onContinue,
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isLoading,
            enabled = state.canContinue
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
