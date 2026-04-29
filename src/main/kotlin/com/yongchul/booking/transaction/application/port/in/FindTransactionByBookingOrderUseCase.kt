package com.yongchul.booking.transaction.application.port.`in`

import com.yongchul.booking.common.Money
import com.yongchul.booking.transaction.domain.TransactionStatus

/**
 * 예약 컨텍스트가 결제 컨텍스트에 "해당 예약 주문의 결제 요약"을 조회하는 포트.
 *
 * 페널티 계산에 필요한 paidAmount, 취소 대상 transactionId 를 제공하기 위해 사용된다.
 */
interface FindTransactionByBookingOrderUseCase {
    fun findLatestPaidByBookingOrderId(bookingOrderId: Long): TransactionSummary?

    data class TransactionSummary(
        val transactionId: Long,
        val bookingOrderId: Long,
        val status: TransactionStatus,
        val paidAmount: Money,
    )
}
