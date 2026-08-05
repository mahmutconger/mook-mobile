package com.mcclabs.mook.domain.model

sealed interface UpdateState {
    data object None : UpdateState
    data object OptionalUpdateAvailable : UpdateState
    data object ForceUpdateRequired : UpdateState
}
