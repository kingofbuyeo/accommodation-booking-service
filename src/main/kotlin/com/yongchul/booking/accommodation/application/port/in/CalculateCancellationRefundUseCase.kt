package com.yongchul.booking.accommodation.application.port.`in`

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 예약 컨텍스트가 숙소 컨텍스트에 "해당 방의 페널티 정책이 산출하는 환불 비율"을 묻는 포트.
 *
 * 페널티 정책 데이터는 숙소가 소유하므로 비율 산출만 숙소가 담당한다.
 * 실제 환불 금액(`paidAmount × ratio`) 계산 책임은 호출자(예약 컨텍스트)에 있다.
 */
interface CalculateCancellationRefundUseCase {
    fun calculateRefundRatio(command: CalculateRefundRatioCommand): RefundRatio

    data class CalculateRefundRatioCommand(
        val roomId: Long,
        val checkInDate: LocalDate,
        val requestedAt: LocalDate = LocalDate.now(),
    )

    data class RefundRatio(
        val appliedRefundRatio: BigDecimal,
        val daysUntilCheckIn: Long,
    )
}
