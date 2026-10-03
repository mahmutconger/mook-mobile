package com.mcclabs.mook.data.room

import com.mcclabs.mook.domain.room.DailyRoomChangeLimitReachedException
import com.mcclabs.mook.domain.room.RoomSwitchErrorCodes

/**
 * `switchRoom` çağrısından gelen ham (Firebase Functions) hatayı alan modeline çevirir.
 *
 * Callable hataları platforma özgü istisna tipleriyle gelir; ortak katmanda güvenilir olan tek
 * sinyal sunucunun `HttpsError` mesajındaki hata kodudur. Tanınmayan hatalar olduğu gibi döner.
 */
fun Throwable.toRoomSwitchDomainError(): Throwable = when {
    this is DailyRoomChangeLimitReachedException -> this
    message?.contains(RoomSwitchErrorCodes.DAILY_LIMIT, ignoreCase = true) == true ->
        DailyRoomChangeLimitReachedException(cause = this)
    else -> this
}
