package com.mcclabs.mook.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import mook.shared.generated.resources.back_svgrepo_com
import mook.shared.generated.resources.room_switch_back_cd
import mook.shared.generated.resources.room_switch_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Lets the user switch their current language room from Discover. */
@Composable
fun RoomSwitchScreen(
    onNavigateBack: () -> Unit,
    viewModel: RoomSwitchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RoomSwitchEvent.Done -> onNavigateBack()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(Res.drawable.back_svgrepo_com),
                    contentDescription = stringResource(Res.string.room_switch_back_cd),
                    tint = NeonColors.TextPrimary
                )
            }
            Text(
                text = stringResource(Res.string.room_switch_title),
                style = MaterialTheme.typography.titleLarge,
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonColors.Primary)
            }
        } else {
            RoomPickerGrid(
                languages = state.languages,
                selectedCode = state.selectedCode,
                onSelect = { viewModel.onRoomSelected(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
