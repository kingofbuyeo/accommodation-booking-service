package com.yongchul.booking.common.infrastructure.kafka

import com.yongchul.booking.common.event.DomainEvent

interface DomainEventPublisher {
    fun publish(topic: String, event: DomainEvent)
}
