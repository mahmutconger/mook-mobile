package com.mcclabs.mook.room

import com.mcclabs.mook.data.room.toRoomSwitchDomainError
import com.mcclabs.mook.domain.room.DailyRoomChangeLimitReachedException
import com.mcclabs.mook.feature.room.RoomSelectionError
import com.mcclabs.mook.feature.room.toRoomSelectionError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** `switchRoom` sunucu hatalarının alan istisnalarına ve ekran durumlarına eşlenmesi. */
class RoomSwitchErrorMappingTest {

    @Test
    fun dailyLimitServerErrorBecomesADomainExceptionKeepingTheCause() {
        val raw = IllegalStateException("RESOURCE_EXHAUSTED: daily-room-switch-limit")
        val mapped = raw.toRoomSwitchDomainError()
        assertIs<DailyRoomChangeLimitReachedException>(mapped)
        assertSame(raw, mapped.cause)
    }

    @Test
    fun unrelatedErrorsPassThroughUnchanged() {
        val raw = IllegalStateException("UNAVAILABLE: network")
        assertSame(raw, raw.toRoomSwitchDomainError())
    }

    @Test
    fun dailyLimitMapsToItsOwnScreenStateNotAGenericError() {
        assertEquals(RoomSelectionError.DAILY_LIMIT, DailyRoomChangeLimitReachedException().toRoomSelectionError())
    }

    @Test
    fun legacySlotLimitIsStillRecognised() {
        assertEquals(RoomSelectionError.SLOT_LIMIT, IllegalStateException("room-slot-limit").toRoomSelectionError())
        assertEquals(RoomSelectionError.UNAVAILABLE, IllegalStateException("boom").toRoomSelectionError())
    }
}
