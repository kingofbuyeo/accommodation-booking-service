package com.yongchul.booking.booking.application.port.`in`

import com.yongchul.booking.common.Money
import java.math.BigDecimal
import java.time.LocalDate

interface PreviewPartialCancellationUseCase {
    fun previewPartialCancellation(command: PreviewPartialCancelCommand): PartialCancellationPreview

    data class PreviewPartialCancelCommand(
        val orderId: Long,
        val lineItemId: Long,
        val cancelCheckIn: LocalDate,
        val cancelCheckOut: LocalDate,
    )
}

interface PartialCancelOrderUseCase {
    fun partialCancelOrder(command: PartialCancelCommand): PartialCancelResult

    data class PartialCancelCommand(
        val orderId: Long,
        val lineItemId: Long,
        val cancelCheckIn: LocalDate,
        val cancelCheckOut: LocalDate,
    )

    data class PartialCancelResult(
        val orderId: Long,
        val refundAmount: Money,
        val penaltyAmount: Money,
        val remainingNights: Int,
        val convertedToFullCancel: Boolean,
    )
}

data class PartialCancellationPreview(
    val orderId: Long,
    val lineItemId: Long,
    val cancelCheckIn: LocalDate,
    val cancelCheckOut: LocalDate,
    val cancelNights: Int,
    val cancelledAmount: Money,
    val refundAmount: Money,
    val penaltyAmount: Money,
    val refundRatio: BigDecimal,
    val partialCancelable: Boolean,
    val reasonCode: String?,
    val reasonMessage: String?,
)
