package com.mcclabs.mook.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.ui.theme.NeonColors
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.room_gate_subtitle
import mook.shared.generated.resources.room_gate_title
import mook.shared.generated.resources.room_error_slot_limit
import mook.shared.generated.resources.room_error_unavailable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Mandatory language-room selection gate shown after login/registration, before the
 * user can enter Discover. If a room is already chosen it forwards immediately.
 */
@Composable
fun RoomGateScreen(
    onProceed: () -> Unit,
    viewModel: RoomGateViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RoomGateEvent.Proceed -> onProceed()
            }
        }
    }

    if (state.isChecking) {
        Box(
            modifier = Modifier.fillMaxSize().background(NeonColors.Background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = NeonColors.Primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.room_gate_title),
            style = MaterialTheme.typography.headlineMedium,
            color = NeonColors.Primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.room_gate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextSecondary
        )
        Spacer(Modifier.height(20.dp))
        RoomPickerGrid(
            languages = state.languages,
            selectedCode = null,
            onSelect = { viewModel.onRoomSelected(it) },
            modifier = Modifier.weight(1f)
        )
        state.error?.let { error ->
            Text(
                text = stringResource(
                    if (error == RoomSelectionError.SLOT_LIMIT) Res.string.room_error_slot_limit
                    else Res.string.room_error_unavailable,
                ),
                color = NeonColors.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}
