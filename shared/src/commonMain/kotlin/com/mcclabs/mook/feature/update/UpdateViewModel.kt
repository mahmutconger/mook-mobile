package com.mcclabs.mook.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.UpdateState
import com.mcclabs.mook.domain.update.ForceUpdateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * `App.kt`'deki zorla-güncelleme kilidi bir UseCase'i doğrudan `koinInject` ile
 * kullandığından bu ViewModel artık o kilit için ZORUNLU değildir — ancak Ayarlar
 * ekranındaki gelecekteki bir "Güncellemeleri kontrol et" butonu gibi, sonucu bir
 * Compose ekranı olarak tüketmek isteyen HERHANGİ bir yer için hazır tutulur.
 */
class UpdateViewModel(
    private val forceUpdateUseCase: ForceUpdateUseCase
) : ViewModel() {

    val updateState: StateFlow<UpdateState> = forceUpdateUseCase.updateState
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
            forceUpdateUseCase.evaluate()
        }
    }

    /** Play Core'un Immediate akışını başlatır — bkz. [ForceUpdateUseCase.startImmediateUpdate]. */
    fun onUpdateNowClicked() {
        viewModelScope.launch {
            forceUpdateUseCase.startImmediateUpdate()
        }
    }
}
