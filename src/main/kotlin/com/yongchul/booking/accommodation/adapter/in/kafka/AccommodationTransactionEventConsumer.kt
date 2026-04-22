package com.yongchul.booking.accommodation.adapter.`in`.kafka

import com.yongchul.booking.accommodation.application.port.`in`.SchedulePreemptionUseCase
import com.yongchul.booking.accommodation.domain.event.BookingConfirmedEvent
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderJpaRepository
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderLineItemJpaRepository
import com.yongchul.booking.booking.domain.BookingStatus
import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.adapter.out.persistence.TransactionJpaRepository
import com.yongchul.booking.transaction.application.port.`in`.ProcessTransactionUseCase
import com.yongchul.booking.transaction.domain.TransactionStatus
import com.yongchul.booking.transaction.domain.event.TransactionCompletedEvent
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 숙소 컨텍스트 — 결제 이벤트 수신
 *
 * TransactionCompleted 수신 시:
 *  - Booking 상태가 REQUESTED → 선점 확정, BookingConfirmed 발행
 *  - Booking 상태가 EXPIRED  → 결제 취소 요청
 */
@Component
@KafkaListener(topics = [KafkaTopics.TRANSACTION_EVENTS], groupId = "accommodation-group")
class AccommodationTransactionEventConsumer(
    private val schedulePreemptionUseCase: SchedulePreemptionUseCase,
    private val bookingOrderJpaRepository: BookingOrderJpaRepository,
    private val lineItemJpaRepository: BookingOrderLineItemJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
    private val processTransactionUseCase: ProcessTransactionUseCase,
    private val eventPublisher: DomainEventPublisher,
) {
    @KafkaHandler
    fun onTransactionCompleted(event: TransactionCompletedEvent) {
        val bookingOrderId = event.bookingOrderId.toLong()
        val booking = bookingOrderJpaRepository.findById(bookingOrderId).orElse(null) ?: return

        if (booking.status == BookingStatus.EXPIRED) {
            val transaction = transactionJpaRepository.findAllByBookingOrderId(bookingOrderId)
                .firstOrNull { it.status == TransactionStatus.PAID } ?: return
            processTransactionUseCase.cancel(
                ProcessTransactionUseCase.CancelCommand(
                    transactionId = transaction.id,
                    reason = "예약 선점 만료 후 결제 완료",
                )
            )
            return
        }

        val lineItems = lineItemJpaRepository.findByBookingOrderId(bookingOrderId)
        lineItems.forEach { lineItem ->
            schedulePreemptionUseCase.confirmPreemption(
                SchedulePreemptionUseCase.ConfirmPreemptionCommand(
                    accommodationId = lineItem.accommodationSnapshot.accommodationId,
                    roomId = lineItem.roomSnapshot.roomId,
                    dateRange = lineItem.dateRange,
                    bookingOrderId = bookingOrderId,
                )
            )
        }

        lineItems.firstOrNull()?.let { first ->
            eventPublisher.publish(
                KafkaTopics.ACCOMMODATION_EVENTS,
                BookingConfirmedEvent(
                    accommodationId = first.accommodationSnapshot.accommodationId,
                    roomId = first.roomSnapshot.roomId,
                    bookingOrderId = event.bookingOrderId,
                )
            )
        }
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(payload: Any) {
        // 알 수 없는 이벤트 타입은 무시
    }
}
