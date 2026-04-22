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
