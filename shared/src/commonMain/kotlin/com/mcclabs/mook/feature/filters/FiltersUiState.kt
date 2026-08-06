package com.mcclabs.mook.feature.filters

import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.domain.model.MatchSettings

data class FiltersUiState(
    val settings: MatchSettings = MatchSettings(),
    val availableCountries: List<Country> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
