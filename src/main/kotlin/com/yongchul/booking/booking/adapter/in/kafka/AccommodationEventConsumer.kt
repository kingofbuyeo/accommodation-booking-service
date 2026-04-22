package com.yongchul.booking.booking.adapter.`in`.kafka

import com.yongchul.booking.accommodation.domain.event.BookingConfirmedEvent
import com.yongchul.booking.accommodation.domain.event.SchedulePreemptionExpiredEvent
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderJpaRepository
import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 예약 컨텍스트 — 숙소 이벤트 수신
 *
 * SchedulePreemptionExpired → Booking EXPIRED 전이
 * BookingConfirmed          → Booking CONFIRMED 전이
 */
@Component
@KafkaListener(topics = [KafkaTopics.ACCOMMODATION_EVENTS], groupId = "booking-group")
class AccommodationEventConsumer(
    private val bookingOrderJpaRepository: BookingOrderJpaRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    @KafkaHandler
    fun onSchedulePreemptionExpired(event: SchedulePreemptionExpiredEvent) {
        val booking = bookingOrderJpaRepository.findById(event.bookingOrderId.toLong()).orElse(null) ?: return
        booking.expire()
    }

    @Transactional
    @KafkaHandler
    fun onBookingConfirmed(event: BookingConfirmedEvent) {
        val booking = bookingOrderJpaRepository.findById(event.bookingOrderId.toLong()).orElse(null) ?: return
        booking.confirm()
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(payload: Any) {
        // 알 수 없는 이벤트 타입은 무시
    }
}
