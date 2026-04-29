package com.yongchul.booking.booking.adapter.`in`.web.dto

import com.yongchul.booking.booking.domain.BookingOrder
import com.yongchul.booking.booking.domain.BookingOrderLineItem
import com.yongchul.booking.booking.domain.BookingStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class BookingOrderResponse(
    val orderId: Long,
    val status: BookingStatus,
    val guestName: String,
    val totalAmount: BigDecimal,
    val currency: String,
    val lineItems: List<LineItemResponse>,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime?,
) {
    data class LineItemResponse(
        val lineItemId: Long,
        val accommodationName: String,
        val roomName: String,
        val checkIn: LocalDate,
        val checkOut: LocalDate,
        val nights: Int,
        val activeNights: Int,
        val lineTotal: BigDecimal,
        /** 부분취소 가능 여부 힌트 (null = 정책 미설정, 부분취소 불가) */
        val partialCancelEnabled: Boolean?,
        val cancelledRanges: List<CancelledRangeResponse>,
    )

    data class CancelledRangeResponse(
        val checkIn: LocalDate,
        val checkOut: LocalDate,
        val nights: Int,
    )

    companion object {
        fun from(order: BookingOrder, lineItems: List<BookingOrderLineItem>) = BookingOrderResponse(
            orderId = order.id,
            status = order.status,
            guestName = order.guestInfo.guestName,
            totalAmount = lineItems.sumOf { it.lineTotal.amount },
            currency = lineItems.firstOrNull()?.lineTotal?.currency ?: "KRW",
            lineItems = lineItems.map { item ->
                LineItemResponse(
                    lineItemId = item.id,
                    accommodationName = item.accommodationSnapshot.accommodationName,
                    roomName = item.roomSnapshot.roomName,
                    checkIn = item.checkIn,
                    checkOut = item.checkOut,
                    nights = item.nights,
                    activeNights = item.activeNights,
                    lineTotal = item.lineTotal.amount,
                    partialCancelEnabled = null, // 실제 정책 조회는 FE 미리보기 API 에서
                    cancelledRanges = item.cancelledDateRanges.map { range ->
                        CancelledRangeResponse(
                            checkIn = range.checkIn,
                            checkOut = range.checkOut,
                            nights = range.nights,
                        )
                    },
                )
            },
            createdAt = order.createdAt,
            expiresAt = order.expiresAt,
        )
    }
}
