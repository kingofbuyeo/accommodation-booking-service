package com.yongchul.booking.common.infrastructure.kafka

import com.yongchul.booking.common.event.DomainEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDomainEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) : DomainEventPublisher {

    override fun publish(topic: String, event: DomainEvent) {
        kafkaTemplate.send(topic, event.kafkaPartitionKey, event)
    }
}
