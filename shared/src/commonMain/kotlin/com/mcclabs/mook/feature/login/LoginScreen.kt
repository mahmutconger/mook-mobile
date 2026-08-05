package com.mcclabs.mook.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.ui.components.CustomAuthTextField
import com.mcclabs.mook.ui.components.GlassmorphismCard
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import mook.shared.generated.resources.ic_email
import mook.shared.generated.resources.lock_keyhole_minimalistic_svgrepo_com__1_
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToRegistration: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is LoginViewModel.LoginNavigationEvent.NavigateToRegistration -> {
                    onNavigateToRegistration()
                }
                is LoginViewModel.LoginNavigationEvent.NavigateToHome -> {
                    onNavigateToHome()
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // App name
        Text(
            text = stringResource(Res.string.login_app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = NeonColors.Primary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = stringResource(Res.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Login form card
        GlassmorphismCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Email field
            CustomAuthTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(Res.string.login_email_label),
                placeholder = stringResource(Res.string.login_email_placeholder),
                modifier = Modifier.fillMaxWidth(),
                isPassword = false,
                isError = state.emailError != null,
                errorMessage = state.emailError,
                leadingIcon = Res.drawable.ic_email
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            CustomAuthTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(Res.string.login_password_label),
                placeholder = stringResource(Res.string.login_password_placeholder),
                modifier = Modifier.fillMaxWidth(),
                isPassword = !state.isPasswordVisible,
                isError = state.passwordError != null,
                errorMessage = state.passwordError,
                leadingIcon = Res.drawable.lock_keyhole_minimalistic_svgrepo_com__1_,
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            painter = painterResource(
                                if (state.isPasswordVisible) {
                                    Res.drawable.eye_closed_svgrepo_com
                                } else {
                                    Res.drawable.eye_svgrepo_com
                                }
                            ),
                            contentDescription = if (state.isPasswordVisible) {
                                stringResource(Res.string.login_hide_password_cd)
                            } else {
                                stringResource(Res.string.login_show_password_cd)
                            },
                            tint = NeonColors.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Forgot Password
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = stringResource(Res.string.login_forgot_password),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonColors.Primary,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // TODO: Navigate to forgot password
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign In button
        NeonPrimaryButton(
            text = stringResource(Res.string.login_sign_in_button),
            onClick = viewModel::login,
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isLoading,
            enabled = !state.isLoading
        )

        // General error message
        if (state.generalError != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.generalError!!,
                style = MaterialTheme.typography.bodySmall,
                color = NeonColors.Error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Sign up prompt
        Row(
            modifier = Modifier.padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.login_dont_have_account),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextSecondary
            )
            Text(
                text = stringResource(Res.string.login_sign_up),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.Primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    viewModel.onSignUpClick()
                }
            )
        }
    }
}
