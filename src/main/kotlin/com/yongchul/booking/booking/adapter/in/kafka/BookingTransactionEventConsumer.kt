package com.yongchul.booking.booking.adapter.`in`.kafka

import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderJpaRepository
import com.yongchul.booking.booking.domain.event.BookingCancelledEvent
import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.domain.event.TransactionCancelledEvent
import com.yongchul.booking.transaction.domain.event.TransactionFullyRefundedEvent
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 예약 컨텍스트 — 결제 이벤트 수신
 *
 * TransactionCancelled      → Booking 이미 EXPIRED, 추가 처리 없음 (로깅용)
 * TransactionFullyRefunded  → Booking 취소됨 전이
 */
@Component
@KafkaListener(topics = [KafkaTopics.TRANSACTION_EVENTS], groupId = "booking-group")
class BookingTransactionEventConsumer(
    private val bookingOrderJpaRepository: BookingOrderJpaRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @KafkaHandler
    fun onTransactionCancelled(event: TransactionCancelledEvent) {
        // Booking은 이미 EXPIRED — 상태 변경 없이 이벤트 수신 확인만
    }

    @Transactional
    @KafkaHandler
    fun onTransactionFullyRefunded(event: TransactionFullyRefundedEvent) {
        val booking = bookingOrderJpaRepository.findById(event.bookingOrderId.toLong()).orElse(null) ?: return
        booking.cancel()
        eventPublisher.publish(
            KafkaTopics.BOOKING_EVENTS,
            BookingCancelledEvent(bookingOrderId = event.bookingOrderId),
        )
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(payload: Any) {
        // 알 수 없는 이벤트 타입은 무시
    }
}
