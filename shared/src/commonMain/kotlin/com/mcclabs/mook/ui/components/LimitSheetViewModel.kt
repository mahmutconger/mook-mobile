package com.mcclabs.mook.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.billing.TrialOffer
import com.mcclabs.mook.domain.billing.TrialOfferUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Limit sayfasının durumu: deneme teklifi yalnızca uygunluk doğrulandıktan sonra dolar. */
data class LimitSheetUiState(val trialOffer: TrialOffer? = null)

/**
 * Limit sayfasının ViewModel'i. Sayfa her açıldığında [refresh] ile deneme uygunluğu yeniden
 * doğrulanır (ör. kullanıcı bu arada abone olduysa teklif kaybolur).
 */
class LimitSheetViewModel(private val trialOfferUseCase: TrialOfferUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(LimitSheetUiState())
    val state: StateFlow<LimitSheetUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.value = LimitSheetUiState(trialOffer = trialOfferUseCase())
        }
    }
}
