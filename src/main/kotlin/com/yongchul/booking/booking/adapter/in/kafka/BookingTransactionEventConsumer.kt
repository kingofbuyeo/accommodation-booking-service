package com.yongchul.booking.booking.adapter.`in`.kafka

import com.yongchul.booking.booking.application.service.BookingOrderDataService
import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.domain.event.TransactionCancelledEvent
import com.yongchul.booking.transaction.domain.event.TransactionFullyRefundedEvent
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 예약 컨텍스트 — 결제 이벤트 수신 (Q3-A 일관 적용 — plain 오케스트레이터).
 *
 * - DB 변경은 [BookingOrderDataService] 의 `@Transactional` 메서드에 위임
 * - 메서드 정상 반환(=commit) 후 반환받은 [PendingEvent] 들을 Kafka 로 발행
 *
 * `TransactionCancelled` 는 Booking 이 이미 EXPIRED 상태이므로 별도 처리 없음 (로깅/확인용).
 * `TransactionFullyRefunded` 는 고객 요청 취소 환불 완료 → Booking 을 CANCELLED 로 전이 + 선점 해제 +
 * `BookingCancelledEvent` 발행.
 */
@Component
@KafkaListener(topics = [KafkaTopics.TRANSACTION_EVENTS], groupId = "booking-group")
class BookingTransactionEventConsumer(
    private val bookingOrderDataService: BookingOrderDataService,
    private val eventPublisher: DomainEventPublisher,
) {
    @KafkaHandler
    fun onTransactionCancelled(event: TransactionCancelledEvent) {
        // Booking 은 이미 EXPIRED — 상태 변경 없이 이벤트 수신 확인만
    }

    @KafkaHandler
    fun onTransactionFullyRefunded(event: TransactionFullyRefundedEvent) {
        val pending = bookingOrderDataService.cancelFromTransactionEventTx(event.bookingOrderId)
        pending.forEach { eventPublisher.publish(it.topic, it.payload) }
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(payload: Any) {
        // 알 수 없는 이벤트 타입은 무시
    }
}
