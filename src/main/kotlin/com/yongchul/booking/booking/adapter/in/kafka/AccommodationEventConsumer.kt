package com.yongchul.booking.booking.adapter.`in`.kafka

import com.yongchul.booking.accommodation.domain.event.BookingConfirmedEvent
import com.yongchul.booking.accommodation.domain.event.SchedulePreemptionExpiredEvent
import com.yongchul.booking.booking.application.service.BookingOrderDataService
import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 예약 컨텍스트 — 숙소 이벤트 수신 (Q3-A 일관 적용 — plain 오케스트레이터).
 *
 * 두 핸들러 모두 DB 변경만 수반하고 외부 Kafka 발행은 없으므로 통상 빈 이벤트 목록을 반환받지만,
 * 일관성 위해 publish 루프를 유지한다 (향후 도메인 이벤트 추가 시 그대로 동작).
 */
@Component
@KafkaListener(topics = [KafkaTopics.ACCOMMODATION_EVENTS], groupId = "booking-group")
class AccommodationEventConsumer(
    private val bookingOrderDataService: BookingOrderDataService,
    private val eventPublisher: DomainEventPublisher,
) {
    @KafkaHandler
    fun onSchedulePreemptionExpired(event: SchedulePreemptionExpiredEvent) {
        val pending = bookingOrderDataService.expireFromEventTx(event.bookingOrderId.toLong())
        pending.forEach { eventPublisher.publish(it.topic, it.payload) }
    }

    @KafkaHandler
    fun onBookingConfirmed(event: BookingConfirmedEvent) {
        val pending = bookingOrderDataService.confirmFromEventTx(event.bookingOrderId.toLong())
        pending.forEach { eventPublisher.publish(it.topic, it.payload) }
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(payload: Any) {
        // 알 수 없는 이벤트 타입은 무시
    }
}
