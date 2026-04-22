package com.yongchul.booking.booking.application.port.`in`

import com.yongchul.booking.booking.domain.BookingOrder
import com.yongchul.booking.booking.domain.vo.GuestInfo
import com.yongchul.booking.common.DateRange

interface PlaceOrderUseCase {
    fun placeOrder(command: PlaceOrderCommand): BookingOrder

    data class PlaceOrderCommand(
        val accommodationId: Long,
        val roomId: Long,
        val dateRange: DateRange,
        val guestInfo: GuestInfo,
    )
}

interface ConfirmOrderUseCase {
    fun confirmOrder(orderId: Long)
}

interface CancelOrderUseCase {
    fun cancelOrder(orderId: Long)
}

interface CheckInUseCase {
    fun checkIn(orderId: Long)
}

interface CheckOutUseCase {
    fun checkOut(orderId: Long)
}
