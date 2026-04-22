package com.yongchul.booking.common

import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Embeddable
data class DateRange(
    val checkIn: LocalDate,
    val checkOut: LocalDate,
) {
    init {
        require(checkOut.isAfter(checkIn)) { "체크아웃은 체크인보다 이후여야 합니다." }
    }

    val nights: Int
        get() = ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    fun dates(): List<LocalDate> =
        checkIn.datesUntil(checkOut).toList()

    fun overlaps(other: DateRange): Boolean =
        checkIn < other.checkOut && checkOut > other.checkIn

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(checkIn) && date.isBefore(checkOut)
}
