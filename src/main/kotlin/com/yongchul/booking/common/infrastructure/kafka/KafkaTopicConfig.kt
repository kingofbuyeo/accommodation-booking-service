package com.yongchul.booking.common.infrastructure.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicConfig {

    /**
     * 파티션 수 = 3: BookingID 기반 파티션 키로 동일 예약 이벤트 순서 보장.
     * 파티션이 1개면 키 기반 라우팅이 의미 없음.
     */
    @Bean
    fun bookingEventsTopic(): NewTopic =
        TopicBuilder.name(KafkaTopics.BOOKING_EVENTS)
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun accommodationEventsTopic(): NewTopic =
        TopicBuilder.name(KafkaTopics.ACCOMMODATION_EVENTS)
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun transactionEventsTopic(): NewTopic =
        TopicBuilder.name(KafkaTopics.TRANSACTION_EVENTS)
            .partitions(3)
            .replicas(1)
            .build()
}
