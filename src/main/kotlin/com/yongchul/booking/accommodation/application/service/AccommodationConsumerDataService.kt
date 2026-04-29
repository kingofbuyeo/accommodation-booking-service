package com.yongchul.booking.accommodation.application.service

import com.yongchul.booking.accommodation.application.port.`in`.SchedulePreemptionUseCase
import com.yongchul.booking.accommodation.domain.event.BookingConfirmedEvent
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderJpaRepository
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderLineItemJpaRepository
import com.yongchul.booking.booking.domain.BookingStatus
import com.yongchul.booking.booking.domain.event.BookingPartiallyCancelledEvent
import com.yongchul.booking.common.DateRange
import com.yongchul.booking.common.event.PendingEvent
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.adapter.out.persistence.TransactionJpaRepository
import com.yongchul.booking.transaction.application.port.`in`.ProcessTransactionUseCase
import com.yongchul.booking.transaction.application.service.TransactionDataService
import com.yongchul.booking.transaction.domain.TransactionStatus
import com.yongchul.booking.transaction.domain.event.TransactionCompletedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 숙소 컨텍스트 컨슈머 DB 작업 전담 서비스.
 *
 * Q3-A 일관 적용 — `AccommodationTransactionEventConsumer` 가 plain 오케스트레이터가 되도록 분리.
 *
 * `TransactionCompleted` 수신 시 처리 분기:
 *   - Booking 이 EXPIRED 인 경우: 결제 자동 취소 (TransactionCancelledEvent 발행)
 *   - Booking 이 정상인 경우: 선점 확정 + BookingConfirmedEvent 발행
 *
 * 모든 DB 변경은 한 트랜잭션에서 수행되고, 발행할 이벤트는 [PendingEvent] 목록으로 반환.
 */
@Service
@Transactional(readOnly = true)
class AccommodationConsumerDataService(
    private val bookingOrderJpaRepository: BookingOrderJpaRepository,
    private val lineItemJpaRepository: BookingOrderLineItemJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
    private val transactionDataService: TransactionDataService,
    private val schedulePreemptionUseCase: SchedulePreemptionUseCase,
) {

    @Transactional
    fun handleTransactionCompletedTx(event: TransactionCompletedEvent): List<PendingEvent> {
        val bookingOrderId = event.bookingOrderId.toLong()
        val booking = bookingOrderJpaRepository.findById(bookingOrderId).orElse(null)
            ?: return emptyList()

        if (booking.status == BookingStatus.EXPIRED) {
            // Booking 이 이미 EXPIRED — 결제 자동 취소
            val transaction = transactionJpaRepository.findAllByBookingOrderId(bookingOrderId)
                .firstOrNull { it.status == TransactionStatus.PAID }
                ?: return emptyList()
            val txCancelledEvent = transactionDataService.cancelAndPersist(
                ProcessTransactionUseCase.CancelCommand(
                    transactionId = transaction.id,
                    reason = "예약 선점 만료 후 결제 완료",
                )
            )
            return listOf(PendingEvent(KafkaTopics.TRANSACTION_EVENTS, txCancelledEvent))
        }

        // 정상 흐름 — 선점 확정 + BookingConfirmedEvent 발행
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
        val first = lineItems.firstOrNull() ?: return emptyList()
        return listOf(
            PendingEvent(
                KafkaTopics.ACCOMMODATION_EVENTS,
                BookingConfirmedEvent(
                    accommodationId = first.accommodationSnapshot.accommodationId,
                    roomId = first.roomSnapshot.roomId,
                    bookingOrderId = event.bookingOrderId,
                )
            )
        )
    }

    /**
     * Week3 신규: 부분취소 이벤트 수신 — 취소된 날짜의 ConfirmedBookingDate 삭제.
     *
     * 멱등 처리: `schedulePreemptionUseCase.releaseConfirmed` 는 이미 삭제된 날짜를 재삭제해도 무해하다.
     */
    @Transactional
    fun handleBookingPartiallyCancelledTx(event: BookingPartiallyCancelledEvent): List<PendingEvent> {
        val cancelledRange = DateRange(
            checkIn = event.cancelledCheckIn,
            checkOut = event.cancelledCheckOut,
        )
        schedulePreemptionUseCase.releaseConfirmed(
            SchedulePreemptionUseCase.ReleaseConfirmedCommand(
                accommodationId = event.accommodationId,
                roomId = event.roomId,
                dateRange = cancelledRange,
                bookingOrderId = event.bookingOrderId.toLong(),
            )
        )
        return emptyList()
    }
}
