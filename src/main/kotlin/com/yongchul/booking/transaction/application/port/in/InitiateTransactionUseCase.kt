package com.yongchul.booking.transaction.application.port.`in`

import com.yongchul.booking.transaction.domain.Transaction

interface InitiateTransactionUseCase {
    fun initiate(command: InitiateCommand): Transaction

    data class InitiateCommand(
        val bookingOrderId: Long,
    )
}
