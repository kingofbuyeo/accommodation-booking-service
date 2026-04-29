package com.yongchul.booking.accommodation.adapter.`in`.kafka

import com.yongchul.booking.accommodation.application.service.AccommodationConsumerDataService
import com.yongchul.booking.booking.domain.event.BookingPartiallyCancelledEvent
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Week3 신규: 숙소 컨텍스트 — 예약 이벤트 수신.
 *
 * [BookingPartiallyCancelledEvent] 수신 시 취소된 날짜의 ConfirmedBookingDate 를 삭제해
 * 해당 날짜를 다른 고객이 예약할 수 있도록 한다.
 *
 * DLQ: 처리 실패 시 Kafka 컨슈머가 재시도 후 DLQ 로 이동. 운영 가이드에 따라 재처리.
 */
@Component
@KafkaListener(topics = [KafkaTopics.BOOKING_EVENTS], groupId = "accommodation-booking-group")
class AccommodationBookingEventConsumer(
    private val accommodationConsumerDataService: AccommodationConsumerDataService,
) {
    @KafkaHandler
    fun onBookingPartiallyCancelled(event: BookingPartiallyCancelledEvent) {
        accommodationConsumerDataService.handleBookingPartiallyCancelledTx(event)
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(payload: Any) {
        // 알 수 없는 이벤트 타입 무시
    }
}
