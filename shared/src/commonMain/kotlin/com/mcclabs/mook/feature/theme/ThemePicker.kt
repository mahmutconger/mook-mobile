package com.mcclabs.mook.feature.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.domain.theme.ThemePreference

@Composable
fun ThemePicker(state: ThemeUiState, onThemeSelected: (ThemePreference) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).selectableGroup()) {
        Text("Görünüm", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        ThemePreference.entries.forEach { preference ->
            val selected = state.preference == preference
            Row(
                Modifier.fillMaxWidth().testTag("themeOption${preference.name}")
                    .selectable(selected = selected, enabled = !state.isLoading, role = Role.RadioButton) {
                        onThemeSelected(preference)
                    }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null, enabled = !state.isLoading)
                Text(
                    text = when (preference) {
                        ThemePreference.System -> "Sistem varsayılanı"
                        ThemePreference.Light -> "Açık tema"
                        ThemePreference.Dark -> "Koyu tema"
                        ThemePreference.Ocean -> "Okyanus · sistem görünümünü izler"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
