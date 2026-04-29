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

    /**
     * 다른 DateRange 가 이 범위에 완전히 포함되는지 검사한다.
     * 부분취소에서 "활성 구간 안에 요청 구간이 들어 있는가" 검증에 사용.
     */
    fun containsRange(other: DateRange): Boolean =
        !other.checkIn.isBefore(checkIn) && !other.checkOut.isAfter(checkOut)

    /**
     * 이 DateRange 의 첫날부터 시작하는 부분 구간인지 검사한다.
     * 부분취소의 "첫날 포함" 위치 검증에 사용.
     */
    fun startsAt(other: DateRange): Boolean =
        other.checkIn == checkIn

    /**
     * 이 DateRange 의 마지막 박을 포함하고 끝나는 구간인지 검사한다.
     * 부분취소의 "마지막날 포함" 위치 검증에 사용.
     */
    fun endsAt(other: DateRange): Boolean =
        other.checkOut == checkOut

    /**
     * 이 DateRange 에서 [other] 구간을 잘라낸 후 남는 구간을 반환한다.
     * 부분취소 정책상 [other] 는 반드시 [this] 의 첫날 포함 또는 마지막날 포함 연속이어야 한다.
     * 두 끝 모두 같으면(= 전체 잘라냄) null 반환.
     */
    fun subtract(other: DateRange): DateRange? {
        require(this.containsRange(other)) {
            "잘라낼 구간이 활성 범위 안에 포함되지 않습니다. 활성=$this, 요청=$other"
        }
        return when {
            this == other -> null
            this.startsAt(other) -> DateRange(checkIn = other.checkOut, checkOut = this.checkOut)
            this.endsAt(other) -> DateRange(checkIn = this.checkIn, checkOut = other.checkIn)
            else -> throw IllegalArgumentException(
                "부분취소는 첫날 또는 마지막날을 포함한 연속 구간만 가능합니다. 활성=$this, 요청=$other"
            )
        }
    }
}
