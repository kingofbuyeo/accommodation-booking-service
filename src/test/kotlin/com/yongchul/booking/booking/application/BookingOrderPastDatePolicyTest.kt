package com.yongchul.booking.booking.application

import com.yongchul.booking.common.DateRange
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 과거 날짜 예약 차단 정책 단위 테스트
 * BookingOrderService 의존성 없이 정책 로직만 검증한다.
 */
class BookingOrderPastDatePolicyTest {

    private fun validateCheckInDate(dateRange: DateRange, today: LocalDate = LocalDate.now()) {
        require(!dateRange.checkIn.isBefore(today)) {
            "체크인 날짜는 오늘 이후여야 합니다. 선택한 날짜: ${dateRange.checkIn}"
        }
    }

    @Test
    fun `오늘 날짜로 체크인하면 통과한다`() {
        val today = LocalDate.now()
        val dateRange = DateRange(today, today.plusDays(2))
        validateCheckInDate(dateRange, today) // 예외 없이 통과
    }

    @Test
    fun `미래 날짜로 체크인하면 통과한다`() {
        val today = LocalDate.now()
        val dateRange = DateRange(today.plusDays(1), today.plusDays(3))
        validateCheckInDate(dateRange, today)
    }

    @Test
    fun `어제 날짜로 체크인하면 예외가 발생한다`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dateRange = DateRange(yesterday, today.plusDays(1))
        assertThatThrownBy { validateCheckInDate(dateRange, today) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("체크인 날짜는 오늘 이후여야 합니다")
            .hasMessageContaining(yesterday.toString())
    }

    @Test
    fun `한 달 전 날짜로 체크인하면 예외가 발생한다`() {
        val today = LocalDate.now()
        val pastDate = today.minusMonths(1)
        val dateRange = DateRange(pastDate, pastDate.plusDays(2))
        assertThatThrownBy { validateCheckInDate(dateRange, today) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("체크인 날짜는 오늘 이후여야 합니다")
    }
}
