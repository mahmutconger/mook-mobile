package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.UpdateState
import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    /**
     * Checks Firebase Remote Config for 'min_required_version' and 'latest_available_version'
     * and compares them against the current app version.
     */
    suspend fun checkUpdateStatus(): UpdateState

    /**
     * Optional flow if we want to continuously observe the status.
     * Often, an initialization check is enough, but this is provided for completeness.
     */
    val updateState: Flow<UpdateState>
}
