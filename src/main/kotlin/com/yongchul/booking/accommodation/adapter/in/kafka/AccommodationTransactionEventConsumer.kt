package com.yongchul.booking.accommodation.adapter.`in`.kafka

import com.yongchul.booking.accommodation.application.service.AccommodationConsumerDataService
import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.domain.event.TransactionCompletedEvent
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 숙소 컨텍스트 — 결제 이벤트 수신 (Q3-A 일관 적용 — plain 오케스트레이터).
 *
 * 모든 DB 작업은 [AccommodationConsumerDataService] 의 `@Transactional` 메서드 안에서 처리되고,
 * 발행할 이벤트는 [PendingEvent] 목록으로 반환받아 commit 후 Kafka 로 발행한다.
 */
@Component
@KafkaListener(topics = [KafkaTopics.TRANSACTION_EVENTS], groupId = "accommodation-group")
class AccommodationTransactionEventConsumer(
    private val accommodationConsumerDataService: AccommodationConsumerDataService,
    private val eventPublisher: DomainEventPublisher,
) {
    @KafkaHandler
    fun onTransactionCompleted(event: TransactionCompletedEvent) {
        val pending = accommodationConsumerDataService.handleTransactionCompletedTx(event)
        pending.forEach { eventPublisher.publish(it.topic, it.payload) }
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(payload: Any) {
        // 알 수 없는 이벤트 타입은 무시
    }
}
