package com.yongchul.booking.booking.adapter.`in`.web.dto

import com.yongchul.booking.booking.application.port.`in`.PartialCancellationPreview
import java.math.BigDecimal
import java.time.LocalDate

data class PartialCancelRequest(
    val cancelCheckIn: LocalDate,
    val cancelCheckOut: LocalDate,
)

data class PartialCancellationPreviewResponse(
    val orderId: Long,
    val lineItemId: Long,
    val cancelCheckIn: LocalDate,
    val cancelCheckOut: LocalDate,
    val cancelNights: Int,
    val cancelledAmount: BigDecimal,
    val refundAmount: BigDecimal,
    val penaltyAmount: BigDecimal,
    val currency: String,
    val refundRatio: BigDecimal,
    val partialCancelable: Boolean,
    val reasonCode: String?,
    val reasonMessage: String?,
) {
    companion object {
        fun from(preview: PartialCancellationPreview) = PartialCancellationPreviewResponse(
            orderId = preview.orderId,
            lineItemId = preview.lineItemId,
            cancelCheckIn = preview.cancelCheckIn,
            cancelCheckOut = preview.cancelCheckOut,
            cancelNights = preview.cancelNights,
            cancelledAmount = preview.cancelledAmount.amount,
            refundAmount = preview.refundAmount.amount,
            penaltyAmount = preview.penaltyAmount.amount,
            currency = preview.cancelledAmount.currency,
            refundRatio = preview.refundRatio,
            partialCancelable = preview.partialCancelable,
            reasonCode = preview.reasonCode,
            reasonMessage = preview.reasonMessage,
        )
    }
}

data class PartialCancelResponse(
    val orderId: Long,
    val refundAmount: BigDecimal,
    val penaltyAmount: BigDecimal,
    val currency: String,
    val remainingNights: Int,
    val convertedToFullCancel: Boolean,
)
