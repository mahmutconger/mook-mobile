package com.mcclabs.mook.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.UpdateState
import com.mcclabs.mook.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val updateRepository: UpdateRepository
) : ViewModel() {

    val updateState: StateFlow<UpdateState> = updateRepository.updateState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UpdateState.None
        )

    init {
        checkUpdate()
    }

    private fun checkUpdate() {
        viewModelScope.launch {
            updateRepository.checkUpdateStatus()
        }
    }
}
