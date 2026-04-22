package com.yongchul.booking.accommodation.domain.vo

import java.time.LocalDate

/**
 * Redis 선점 키: accommodation:{accommodationId}:room:{roomId}:date:{date}
 * 날짜별로 분리하여 부분 선점/해제가 가능하도록 설계
 *
 * Redis Value = bookingOrderId (String)
 * TTL 만료 시 keyspace notification 으로 bookingOrderId 를 추출하여
 * SchedulePreemptionExpiredEvent 의 kafkaPartitionKey 로 사용
 */
data class SchedulePreemptionKey(
    val accommodationId: Long,
    val roomId: Long,
    val date: LocalDate,
) {
    fun toRedisKey(): String =
        "accommodation:$accommodationId:room:$roomId:date:$date"

    companion object {
        fun of(accommodationId: Long, roomId: Long, date: LocalDate) =
            SchedulePreemptionKey(accommodationId, roomId, date)
    }
}