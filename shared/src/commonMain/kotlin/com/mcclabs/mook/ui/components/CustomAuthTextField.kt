package com.mcclabs.mook.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.eye_closed_svgrepo_com
import mook.shared.generated.resources.eye_svgrepo_com
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A dark-themed text field designed for authentication and form screens.
 *
 * Features:
 * - Dark [NeonColors.InputBackground] fill with no default underline indicator.
 * - Cyan cursor and focus highlight.
 * - Optional password visibility toggle (eye icon).
 * - Error state: red border and error message below the field.
 * - A label rendered above the field using `labelMedium` style.
 *
 * @param value            Current text value.
 * @param onValueChange    Callback when text changes.
 * @param label            Label displayed above the field.
 * @param placeholder      Placeholder text inside the field.
 * @param modifier         Optional [Modifier].
 * @param isPassword       Enables secure text entry with visibility toggle.
 * @param isError          When `true`, applies error styling.
 * @param errorMessage     Optional message displayed below the field on error.
 * @param trailingIcon     Optional trailing composable icon.
 * @param singleLine       Whether the field constrains to a single line.
 * @param minLines         Minimum visible lines when [singleLine] is `false`.
 */
@Composable
fun CustomAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: DrawableResource? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        isError -> NeonColors.Error
        else -> Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Label above the field
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NeonColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Text field
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextTertiary,
                )
            },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            isError = isError,
            visualTransformation = when {
                isPassword && !passwordVisible -> PasswordVisualTransformation()
                else -> VisualTransformation.None
            },
            keyboardOptions = when {
                isPassword -> KeyboardOptions(keyboardType = KeyboardType.Password)
                else -> KeyboardOptions.Default
            },
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = NeonColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            trailingIcon = when {
                isPassword -> {
                    {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) {
                                        Res.drawable.eye_closed_svgrepo_com
                                    } else {
                                        Res.drawable.eye_svgrepo_com
                                    }
                                ),
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                                tint = NeonColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                trailingIcon != null -> trailingIcon
                else -> null
            },
            colors = TextFieldDefaults.colors(
                focusedTextColor = NeonColors.TextPrimary,
                unfocusedTextColor = NeonColors.TextPrimary,
                cursorColor = NeonColors.Primary,
                focusedContainerColor = NeonColors.InputBackground,
                unfocusedContainerColor = NeonColors.InputBackground,
                errorContainerColor = NeonColors.InputBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                errorCursorColor = NeonColors.Error,
            ),
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = shape,
                ),
        )

        // Error message
        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = NeonColors.Error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
