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

    /**
     * Week3 신규: 부분취소 환불.
     * 동일 [RefundPartialCommand.requestKey] 로 이미 처리된 경우 멱등(같은 결과 반환).
     */
    fun refundPartial(command: RefundPartialCommand): RefundPartialResult

    data class CancelWithPenaltyCommand(
        val transactionId: Long,
        val refundAmount: RefundAmount,
    )

    data class RefundPartialCommand(
        val transactionId: Long,
        val refundAmount: RefundAmount,
        val requestKey: String,
    )

    data class RefundPartialResult(
        val transactionId: Long,
        val requestKey: String,
        val refundAmount: RefundAmount,
        /** true 면 같은 요청키로 이미 처리되어 신규 환불이 일어나지 않음. */
        val alreadyProcessed: Boolean,
    )
}