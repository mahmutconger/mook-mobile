package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.model.UpdateState
import com.mcclabs.mook.domain.repository.UpdateRepository
import com.mcclabs.mook.platform.getAppVersion
import com.mcclabs.mook.util.Log
import com.mcclabs.mook.util.compareSemVer
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UpdateRepositoryImpl : UpdateRepository {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.None)
    override val updateState: Flow<UpdateState> = _updateState.asStateFlow()

    override suspend fun checkUpdateStatus(): UpdateState {
        val remoteConfig = Firebase.remoteConfig
        
        return try {
            // Fetch and activate configs
            remoteConfig.fetchAndActivate()

            val minRequiredVersion = remoteConfig.getValue("min_required_version").asString()
            val latestAvailableVersion = remoteConfig.getValue("latest_available_version").asString()
            val currentVersion = getAppVersion()

            val state = when {
                minRequiredVersion.isNotBlank() && compareSemVer(currentVersion, minRequiredVersion) < 0 -> {
                    UpdateState.ForceUpdateRequired
                }
                latestAvailableVersion.isNotBlank() && compareSemVer(currentVersion, latestAvailableVersion) < 0 -> {
                    UpdateState.OptionalUpdateAvailable
                }
                else -> {
                    UpdateState.None
                }
            }

            _updateState.value = state
            state
        } catch (e: Exception) {
            Log.e("Failed to fetch Remote Config for updates", e)
            UpdateState.None
        }
    }
}
