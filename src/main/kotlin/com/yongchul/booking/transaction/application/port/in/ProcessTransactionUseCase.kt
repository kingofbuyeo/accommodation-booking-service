package com.yongchul.booking.transaction.application.port.`in`

import com.yongchul.booking.transaction.domain.vo.LedgerInfo

interface ProcessTransactionUseCase {
    fun complete(command: CompleteCommand)
    fun fail(transactionId: Long)
    fun cancel(command: CancelCommand)

    data class CompleteCommand(
        val transactionId: Long,
        val ledgerInfo: LedgerInfo,
    )

    data class CancelCommand(
        val transactionId: Long,
        val reason: String,
    )
}
