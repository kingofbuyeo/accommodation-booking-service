package com.yongchul.booking.booking.domain.event

import com.yongchul.booking.common.event.DomainEvent
import java.time.LocalDateTime

sealed interface BookingDomainEvent : DomainEvent

data class BookingInitiatedEvent(
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BookingDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

data class BookingCancelledEvent(
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BookingDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

data class CheckInRecordedEvent(
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BookingDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

data class CheckOutRecordedEvent(
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BookingDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

/**
 * Week3 신규: 예약 부분취소 완료 이벤트.
 *
 * 발행: 예약 컨텍스트 (결제 부분환불 성공 + 라인 아이템 활성/취소 리스트 갱신 후 commit 직후)
 * 수신: 숙소 컨텍스트 — 취소된 날짜의 ConfirmedBookingDate 삭제 (멱등 처리)
 *
 * [requestKey] = `hash(예약ID + 정렬된 부분취소 날짜)`. 컨슈머 측 멱등성 검사에도 사용.
 */
data class BookingPartiallyCancelledEvent(
    val bookingOrderId: String,
    val lineItemId: Long,
    val accommodationId: Long,
    val roomId: Long,
    val cancelledCheckIn: java.time.LocalDate,
    val cancelledCheckOut: java.time.LocalDate,
    val requestKey: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BookingDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}
