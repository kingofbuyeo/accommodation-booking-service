package com.yongchul.booking.booking.domain

import com.yongchul.booking.booking.domain.vo.AccommodationSnapshot
import com.yongchul.booking.booking.domain.vo.GuestInfo
import com.yongchul.booking.booking.domain.vo.RoomSnapshot
import com.yongchul.booking.common.DateRange
import com.yongchul.booking.common.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BookingOrderTest {

    private val guestInfo = GuestInfo("김용철", "010-0000-0000", 2)
    private val accommodationSnapshot = AccommodationSnapshot(1L, "제주 게스트하우스", "제주시 어딘가", "호스트A")
    private val roomSnapshot = RoomSnapshot(1L, "바다뷰 스위트", 2, Money.of(150_000))
    private val dateRange = DateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3))

    private fun createOrder(): BookingOrder {
        val order = BookingOrder(guestInfo = guestInfo)
        order.addLineItem(accommodationSnapshot, roomSnapshot, dateRange)
        return order
    }

    @Test
    fun `라인 아이템 추가 시 총 금액은 단가 x 박수`() {
        val order = createOrder()
        assertThat(order.totalAmount).isEqualTo(Money.of(300_000))
    }

    @Test
    fun `REQUESTED 상태에서 confirm 하면 CONFIRMED가 된다`() {
        val order = createOrder()
        order.confirm()
        assertThat(order.status).isEqualTo(BookingStatus.CONFIRMED)
    }

    @Test
    fun `CONFIRMED 상태에서 checkIn 하면 CHECKED_IN이 된다`() {
        val order = createOrder()
        order.confirm()
        order.checkIn()
        assertThat(order.status).isEqualTo(BookingStatus.CHECKED_IN)
    }

    @Test
    fun `체크인 없이 체크아웃하면 예외가 발생한다`() {
        val order = createOrder()
        order.confirm()
        assertThatThrownBy { order.checkOut() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("체크인 없이 체크아웃할 수 없습니다")
    }

    @Test
    fun `체크인 이후 취소하면 예외가 발생한다`() {
        val order = createOrder()
        order.confirm()
        order.checkIn()
        assertThatThrownBy { order.cancel() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("체크인 이후에는 취소할 수 없습니다")
    }

    @Test
    fun `취소된 예약을 재확정하면 예외가 발생한다`() {
        val order = createOrder()
        order.cancel()
        assertThatThrownBy { order.confirm() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("REQUESTED 상태에서만 확정할 수 있습니다")
    }

    @Test
    fun `REQUESTED 상태에서 expire 하면 EXPIRED가 된다`() {
        val order = createOrder()
        order.expire()
        assertThat(order.status).isEqualTo(BookingStatus.EXPIRED)
    }

    @Test
    fun `EXPIRED 상태에서 confirm 하면 예외가 발생한다`() {
        val order = createOrder()
        order.expire()
        assertThatThrownBy { order.confirm() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("REQUESTED 상태에서만 확정할 수 있습니다")
    }

    @Test
    fun `CONFIRMED 상태에서 expire 하면 예외가 발생한다`() {
        val order = createOrder()
        order.confirm()
        assertThatThrownBy { order.expire() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("REQUESTED 상태에서만 만료 처리할 수 있습니다")
    }
}
