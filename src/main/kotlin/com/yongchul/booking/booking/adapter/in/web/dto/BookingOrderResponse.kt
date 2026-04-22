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
        val accommodationName: String,
        val roomName: String,
        val checkIn: LocalDate,
        val checkOut: LocalDate,
        val nights: Int,
        val lineTotal: BigDecimal,
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
                    accommodationName = item.accommodationSnapshot.accommodationName,
                    roomName = item.roomSnapshot.roomName,
                    checkIn = item.checkIn,
                    checkOut = item.checkOut,
                    nights = item.nights,
                    lineTotal = item.lineTotal.amount,
                )
            },
            createdAt = order.createdAt,
            expiresAt = order.expiresAt,
        )
    }
}
