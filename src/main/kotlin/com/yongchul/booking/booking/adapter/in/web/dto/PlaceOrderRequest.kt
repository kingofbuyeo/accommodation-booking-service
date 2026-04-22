package com.yongchul.booking.booking.adapter.`in`.web.dto

import com.yongchul.booking.booking.application.port.`in`.PlaceOrderUseCase
import com.yongchul.booking.booking.domain.vo.GuestInfo
import com.yongchul.booking.common.DateRange
import java.time.LocalDate

data class PlaceOrderRequest(
    val accommodationId: Long,
    val roomId: Long,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guestName: String,
    val phone: String,
    val headcount: Int,
) {
    fun toCommand() = PlaceOrderUseCase.PlaceOrderCommand(
        accommodationId = accommodationId,
        roomId = roomId,
        dateRange = DateRange(checkIn, checkOut),
        guestInfo = GuestInfo(guestName, phone, headcount),
    )
}
