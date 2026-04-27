package com.yongchul.booking.accommodation.domain

import com.yongchul.booking.common.DateRange
import com.yongchul.booking.common.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RoomTest {

    private fun createRoom() = Room(
        id = 1L,
        accommodationId = 1L,
        name = "바다뷰 스위트",
        capacity = 2,
        pricePerNight = Money.of(150_000),
    )

    @Test
    fun `차단 일정이 없으면 해당 기간은 예약 가능하다`() {
        val room = createRoom()
        val dateRange = DateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3))
        assertThat(room.isAvailableFor(schedules = emptyList(), dateRange = dateRange)).isTrue()
    }

    @Test
    fun `체크인 날짜가 차단되어 있으면 예약 불가다`() {
        val room = createRoom()
        val blockedDate = LocalDate.of(2026, 5, 1)
        val schedule = RoomSchedule(
            roomId = room.id,
            blockedDate = blockedDate,
            type = RoomScheduleType.BLOCKED,
            reason = null,
        )
        val dateRange = DateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3))
        assertThat(room.isAvailableFor(schedules = listOf(schedule), dateRange = dateRange)).isFalse()
    }

    @Test
    fun `총 금액은 단가 x 박수다`() {
        val room = createRoom()
        val dateRange = DateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4))
        assertThat(room.calculateTotalPrice(dateRange)).isEqualTo(Money.of(450_000))
    }
}
