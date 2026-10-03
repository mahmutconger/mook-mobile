package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.repository.MookProfileRepository
import com.mcclabs.mook.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** [MookProfileRepository]'nin `activateMookProfile` callable'ı üzerinden uygulaması. */
class MookProfileRepositoryImpl : MookProfileRepository {

    override suspend fun activate(): Boolean {
        if (Firebase.auth.currentUser == null) return false
        return try {
            appHttpsCallable("activateMookProfile").invoke().data<ActivationResponse>().active
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("Mook profili etkinleştirilemedi; bir sonraki açılışta yeniden denenecek", error)
            false
        }
    }
}

@Serializable
private data class ActivationResponse(val active: Boolean = false, val activatedNow: Boolean = false)
