package com.yongchul.booking.accommodation.domain.event

import com.yongchul.booking.common.event.DomainEvent
import java.time.LocalDateTime

sealed interface AccommodationDomainEvent : DomainEvent

/**
 * 일정 선점 이벤트.
 *
 * 발행: 숙소 컨텍스트 (예약하기 클릭 시 Redis SETNX 성공)
 */
data class SchedulePreemptedEvent(
    val accommodationId: Long,
    val roomId: Long,
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : AccommodationDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

/**
 * 선점 TTL 만료 이벤트.
 *
 * 발행: 숙소 컨텍스트 (Redis keyspace notification 수신 시)
 * 수신: 예약 컨텍스트 → Booking 상태 EXPIRED 전이
 *
 * kafkaPartitionKey = bookingOrderId: SchedulePreemptionExpired 와 TransactionCompleted 가
 * 동일 파티션에 순서대로 처리되어 결제 완료 시점에 Booking 상태가 이미 EXPIRED 임을 보장
 */
data class SchedulePreemptionExpiredEvent(
    val accommodationId: Long,
    val roomId: Long,
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : AccommodationDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

/**
 * 일정 확정 이벤트.
 *
 * 발행: 숙소 컨텍스트 (TransactionCompleted 수신 후 선점 유효 확인 시)
 * 수신: 예약 컨텍스트 → Booking 상태 CONFIRMED 전이
 */
data class BookingConfirmedEvent(
    val accommodationId: Long,
    val roomId: Long,
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : AccommodationDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}