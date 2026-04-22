package com.yongchul.booking.transaction.application.port.`in`

import com.yongchul.booking.transaction.domain.vo.RefundAmount

interface RefundTransactionUseCase {
    fun partialRefund(command: PartialRefundCommand)

    data class PartialRefundCommand(
        val transactionId: Long,
        val refundAmount: RefundAmount,
    )
}
