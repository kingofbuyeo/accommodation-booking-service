package com.yongchul.booking.booking.adapter.`in`.web.dto

import com.yongchul.booking.booking.application.port.`in`.PreviewCancellationUseCase
import java.math.BigDecimal

data class CancellationPreviewResponse(
    val bookingOrderId: Long,
    val paidAmount: BigDecimal,
    val refundAmount: BigDecimal,
    val penaltyAmount: BigDecimal,
    val currency: String,
    val appliedRefundRatio: BigDecimal,
    val daysUntilCheckIn: Long,
    val cancelable: Boolean,
    val reasonIfNotCancelable: String?,
) {
    companion object {
        fun from(preview: PreviewCancellationUseCase.CancellationPreview) = CancellationPreviewResponse(
            bookingOrderId = preview.bookingOrderId,
            paidAmount = preview.paidAmount.amount,
            refundAmount = preview.refundAmount.amount,
            penaltyAmount = preview.penaltyAmount.amount,
            currency = preview.paidAmount.currency,
            appliedRefundRatio = preview.appliedRefundRatio,
            daysUntilCheckIn = preview.daysUntilCheckIn,
            cancelable = preview.cancelable,
            reasonIfNotCancelable = preview.reasonIfNotCancelable,
        )
    }
}
