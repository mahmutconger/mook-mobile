package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.repository.UserTimeZoneRepository
import com.mcclabs.mook.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/**
 * [UserTimeZoneRepository]'nin `updateTimeZone` callable'ı üzerinden uygulaması.
 *
 * @param deviceTimeZoneId Cihazın IANA saat dilimi kimliğini döner; testlerde değiştirilebilir.
 */
class UserTimeZoneRepositoryImpl(
    private val deviceTimeZoneId: () -> String = { TimeZone.currentSystemDefault().id },
) : UserTimeZoneRepository {

    override suspend fun syncDeviceTimeZone(): Boolean {
        if (Firebase.auth.currentUser == null) return false
        val timeZone = deviceTimeZoneId()
        // Sunucu yalnızca geçerli IANA kimliklerini kabul eder; "Z" veya "+03:00" gibi sabit
        // ofsetler gün sınırı hesabı için anlamlı değildir ve hiç gönderilmez.
        if (!isIanaZoneId(timeZone)) return false
        return try {
            appHttpsCallable("updateTimeZone")
                .invoke(UpdateTimeZoneRequest(timeZone))
                .data<UpdateTimeZoneResponse>()
                .timeZone == timeZone
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error.message?.contains(COOLDOWN_ERROR) == true) {
                // Beklenen durum: saat dilimi son 7 gün içinde zaten değişti.
                Log.d("Saat dilimi bekleme süresi dolmadığı için güncellenmedi: $timeZone")
            } else {
                Log.e("Saat dilimi sunucuyla eşitlenemedi; bir sonraki açılışta yeniden denenecek", error)
            }
            false
        }
    }

    private fun isIanaZoneId(id: String): Boolean =
        (id.length in 1..64 && '/' in id) || id == "UTC"

    private companion object {
        const val COOLDOWN_ERROR = "timezone-change-cooldown"
    }
}

@Serializable
private data class UpdateTimeZoneRequest(val timeZone: String)

@Serializable
private data class UpdateTimeZoneResponse(val timeZone: String = "", val changed: Boolean = false)
