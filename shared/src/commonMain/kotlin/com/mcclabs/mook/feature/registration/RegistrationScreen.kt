package com.mcclabs.mook.feature.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.model.Gender
import com.mcclabs.mook.ui.components.AvatarPicker
import com.mcclabs.mook.ui.components.CountryDropdown
import com.mcclabs.mook.ui.components.CustomAuthTextField
import com.mcclabs.mook.ui.components.LanguageDropdown
import com.mcclabs.mook.ui.components.NeonChip
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.components.NeonToggle
import com.mcclabs.mook.ui.components.SegmentedPicker
import com.mcclabs.mook.ui.components.StepIndicator
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.util.rememberGalleryPicker
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val TOTAL_STEPS = 4

/** Formats a birth date (epoch millis, UTC) as an ISO-style "yyyy-MM-dd" string. */
private fun formatBirthDate(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date
    val month = date.monthNumber.toString().padStart(2, '0')
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onNavigateToHome: () -> Unit,
    onNavigateBack: () -> Unit = {},
    // Gereksinim 2.13 (Faz 4): Remote Config bayrağı açıkken onboarding sonunda
    // doğrudan Deneme paketi ön-seçili Paywall'a yönlendirmek için.
    onNavigateToPaywallWithTrial: () -> Unit = onNavigateToHome,
    viewModel: RegistrationViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val avatarPicker = rememberGalleryPicker(onImagePicked = viewModel::onAvatarSelected)
    val discoveryPhotoPicker = rememberGalleryPicker(onImagePicked = viewModel::onDiscoveryPhotoSelected)

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is RegistrationViewModel.RegistrationNavigationEvent.NavigateToHome -> onNavigateToHome()
                is RegistrationViewModel.RegistrationNavigationEvent.NavigateToPaywallWithTrial ->
                    onNavigateToPaywallWithTrial()
            }
        }
    }

    // Date-of-birth picker dialog (used in step 2).
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.birthDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onBirthDateChange(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text(stringResource(Res.string.registration_date_picker_ok), color = NeonColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.registration_date_picker_cancel), color = NeonColors.TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (state.isAgeBlocked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NeonColors.Background)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.registration_age_blocked_error),
                style = MaterialTheme.typography.headlineMedium,
                color = NeonColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: back (to previous step, or Login on step 1) + step indicator.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (state.currentStep > 1) viewModel.onPreviousStep() else onNavigateBack()
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.back_svgrepo_com),
                    contentDescription = "Back",
                    tint = NeonColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            StepIndicator(totalSteps = TOTAL_STEPS, currentStep = state.currentStep - 1)
            Spacer(modifier = Modifier.weight(1f))
            // Balances the back button so the indicator stays centered.
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        val (title, subtitle) = when (state.currentStep) {
            1 -> stringResource(Res.string.registration_step1_title) to stringResource(Res.string.registration_step1_subtitle)
            2 -> stringResource(Res.string.registration_step2_title) to stringResource(Res.string.registration_step2_subtitle)
            3 -> stringResource(Res.string.registration_step3_title) to stringResource(Res.string.registration_step3_subtitle)
            else -> stringResource(Res.string.registration_step4_title) to stringResource(Res.string.registration_step4_subtitle)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = NeonColors.Primary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Scrollable step content fills the space above the fixed bottom button.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (state.currentStep) {
                1 -> StepAgeGate(state, onOpenDatePicker = { showDatePicker = true })
                2 -> StepLanguages(state, viewModel)
                3 -> StepAboutYou(state, viewModel)
                else -> StepAccount(state, viewModel, avatarPicker::launch, discoveryPhotoPicker::launch)
            }
        }

        if (state.generalError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.generalError!!,
                style = MaterialTheme.typography.bodySmall,
                color = NeonColors.Error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val isLastStep = state.currentStep == TOTAL_STEPS
        NeonPrimaryButton(
            text = if (isLastStep) stringResource(Res.string.registration_create_account) else stringResource(Res.string.registration_continue),
            onClick = { if (isLastStep) viewModel.completeProfile() else viewModel.onNextStep() },
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isLoading,
            enabled = !state.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/* ---------------------------------------------------------------------------- */
/* Step 1 — Birth date (Age Gate)                                               */
/* ---------------------------------------------------------------------------- */

@Composable
private fun ColumnScope.StepAgeGate(
    state: RegistrationUiState,
    onOpenDatePicker: () -> Unit
) {
    Text(
        text = stringResource(Res.string.registration_date_of_birth_label),
        style = MaterialTheme.typography.labelMedium,
        color = NeonColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeonColors.InputBackground)
            .clickable(onClick = onOpenDatePicker)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.birthDateMillis?.let { formatBirthDate(it) } ?: stringResource(Res.string.registration_select_birth_date),
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.birthDateMillis != null) NeonColors.TextPrimary else NeonColors.TextTertiary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(Res.drawable.calendar_svgrepo_com),
            contentDescription = "Pick date",
            tint = NeonColors.Primary,
            modifier = Modifier.size(24.dp)
        )
    }
    FieldError(state.birthDateError)
}

/* ---------------------------------------------------------------------------- */
/* Step 2 — languages + country                                                 */
/* ---------------------------------------------------------------------------- */

@Composable
private fun ColumnScope.StepLanguages(state: RegistrationUiState, viewModel: RegistrationViewModel) {
    LanguageDropdown(
        label = stringResource(Res.string.registration_language_label),
        selectedLanguage = state.selectedLanguage,
        onLanguageSelected = viewModel::onLanguageChange,
        languages = state.availableLanguages
    )

    Spacer(modifier = Modifier.height(20.dp))

    CountryDropdown(
        label = stringResource(Res.string.registration_country_label),
        selectedCountry = state.selectedCountry,
        onCountrySelected = viewModel::onCountryChange,
        countries = state.availableCountries
    )
    FieldError(state.countryError)
}

/* ---------------------------------------------------------------------------- */
/* Step 3 — name, gender                                                        */
/* ---------------------------------------------------------------------------- */

@Composable
private fun ColumnScope.StepAboutYou(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel
) {
    CustomAuthTextField(
        value = state.displayName,
        onValueChange = viewModel::onDisplayNameChange,
        label = stringResource(Res.string.registration_full_name_label),
        placeholder = stringResource(Res.string.registration_full_name_placeholder),
        modifier = Modifier.fillMaxWidth(),
        isError = state.displayNameError != null,
        errorMessage = state.displayNameError,
        leadingIcon = Res.drawable.ic_user
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Gender
    Text(
        text = stringResource(Res.string.registration_gender_label),
        style = MaterialTheme.typography.labelMedium,
        color = NeonColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    SegmentedPicker(
        options = listOf(
            Gender.MALE to stringResource(Res.string.registration_gender_male),
            Gender.FEMALE to stringResource(Res.string.registration_gender_female),
            Gender.OTHER to stringResource(Res.string.registration_gender_other)
        ),
        selectedValue = state.gender,
        onSelected = viewModel::onGenderChange
    )
    FieldError(state.genderError)
}

/* ---------------------------------------------------------------------------- */
/* Step 3 — photo, account, discovery photos, bio, interests, terms             */
/* ---------------------------------------------------------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.StepAccount(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel,
    onPickAvatar: () -> Unit,
    onPickDiscoveryPhoto: () -> Unit
) {
    AvatarPicker(
        avatarUri = state.avatarUri,
        onPickAvatar = onPickAvatar,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 20.dp)
    )

    CustomAuthTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(Res.string.registration_email_label),
        placeholder = stringResource(Res.string.registration_email_placeholder),
        modifier = Modifier.fillMaxWidth(),
        isError = state.emailError != null,
        errorMessage = state.emailError,
        leadingIcon = Res.drawable.ic_email
    )

    Spacer(modifier = Modifier.height(16.dp))

    CustomAuthTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(Res.string.registration_password_label),
        placeholder = stringResource(Res.string.registration_password_placeholder),
        modifier = Modifier.fillMaxWidth(),
        isPassword = true,
        isError = state.passwordError != null,
        errorMessage = state.passwordError,
        leadingIcon = Res.drawable.lock_keyhole_minimalistic_svgrepo_com__1_
    )

    Spacer(modifier = Modifier.height(16.dp))

    CustomAuthTextField(
        value = state.confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChange,
        label = stringResource(Res.string.registration_confirm_password_label),
        placeholder = stringResource(Res.string.registration_confirm_password_placeholder),
        modifier = Modifier.fillMaxWidth(),
        isPassword = true,
        isError = state.confirmPasswordError != null,
        errorMessage = state.confirmPasswordError,
        leadingIcon = Res.drawable.lock_keyhole_minimalistic_svgrepo_com__1_
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Discovery photos
    Text(
        text = stringResource(Res.string.registration_discovery_photos_label),
        style = MaterialTheme.typography.labelSmall,
        color = NeonColors.TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.discoveryPhotos.forEach { uri ->
            AsyncImage(
                model = uri,
                contentDescription = stringResource(Res.string.registration_discovery_photo_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonColors.Surface)
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NeonColors.CardBorder)
                .clickable(onClick = onPickDiscoveryPhoto),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = NeonColors.Primary, style = MaterialTheme.typography.headlineMedium)
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Bio
    CustomAuthTextField(
        value = state.bio,
        onValueChange = viewModel::onBioChange,
        label = stringResource(Res.string.registration_bio_label),
        placeholder = stringResource(Res.string.registration_bio_placeholder),
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        minLines = 3,
        isError = state.bioError != null,
        errorMessage = state.bioError
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Interests
    Text(
        text = stringResource(Res.string.registration_interests_label),
        style = MaterialTheme.typography.labelMedium,
        color = NeonColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.availableInterests.forEach { interest ->
            NeonChip(
                text = com.mcclabs.mook.ui.components.interestLabel(interest),
                isSelected = interest in state.selectedInterests,
                onToggle = { viewModel.toggleInterest(interest) }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Show in Discover toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.registration_show_in_discover_title),
                style = MaterialTheme.typography.bodyLarge,
                color = NeonColors.TextPrimary
            )
            Text(
                text = stringResource(Res.string.registration_show_in_discover_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = NeonColors.TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        NeonToggle(
            checked = state.discoverVisible,
            onCheckedChange = viewModel::onDiscoverVisibleChange
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Terms of Use
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeonColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.legal_consent_paragraph),
            style = MaterialTheme.typography.bodySmall,
            color = NeonColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.onTermsAcceptedChange(!state.termsAccepted) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.termsAccepted,
                onCheckedChange = viewModel::onTermsAcceptedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonColors.Primary,
                    uncheckedColor = NeonColors.CardBorder,
                    checkmarkColor = NeonColors.Background
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.legal_accept_terms),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextPrimary
            )
        }
    }
    FieldError(if (state.termsError != null) stringResource(Res.string.legal_terms_error) else null)

    Spacer(modifier = Modifier.height(8.dp))

    // EULA (End User License Agreement) & UGC Policy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.onEulaToggle(!state.acceptedEula) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = state.acceptedEula,
            onCheckedChange = viewModel::onEulaToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = NeonColors.Primary,
                uncheckedColor = NeonColors.CardBorder,
                checkmarkColor = NeonColors.Background
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = stringResource(Res.string.legal_accept_eula),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextPrimary
            )
            Text(
                text = stringResource(Res.string.legal_zero_tolerance),
                style = MaterialTheme.typography.labelSmall,
                color = NeonColors.TextSecondary
            )
        }
    }
    FieldError(if (!state.acceptedEula && state.generalError?.contains("EULA") == true) stringResource(Res.string.legal_eula_error) else null)

    Spacer(modifier = Modifier.height(16.dp))

    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri("https://walktalkk.com/legal.html") },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(Res.string.registration_read_privacy_policy),
            style = MaterialTheme.typography.labelMedium,
            color = NeonColors.Primary
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

/** Small inline error line shown under a field when [message] is non-null. */
@Composable
private fun ColumnScope.FieldError(message: String?) {
    if (message != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = NeonColors.Error,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
