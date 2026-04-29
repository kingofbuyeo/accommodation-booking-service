package com.yongchul.booking.transaction.application.port.`in`

import com.yongchul.booking.transaction.domain.vo.RefundAmount

/**
 * 고객 요청 취소에 의한 환불 처리.
 *
 * 페널티 계산은 숙소 컨텍스트의 책임이며, 이 포트는 계산된 환불 금액만 전달받는다.
 * 한 번의 호출로 Transaction 이 FULLY_CANCELLED 로 전이되며 이후 추가 환불은 허용되지 않는다.
 */
interface RefundTransactionUseCase {
    fun cancelWithPenalty(command: CancelWithPenaltyCommand)

    data class CancelWithPenaltyCommand(
        val transactionId: Long,
        val refundAmount: RefundAmount,
    )
}