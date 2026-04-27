package com.yongchul.booking.booking.adapter.`in`.scheduler

import com.yongchul.booking.accommodation.application.service.AccommodationService
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderJpaRepository
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderLineItemJpaRepository
import com.yongchul.booking.booking.application.port.`in`.CheckInUseCase
import com.yongchul.booking.booking.domain.BookingStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Q2a 결정 — 자동 체크인 처리.
 *
 * "체크인 이후 취소 불가" 불변조건을 시스템적으로 보장하기 위해, 숙소별 체크인 시각이 지난
 * CONFIRMED 예약은 일정 주기마다 자동으로 CHECKED_IN 상태로 전이시킨다.
 *
 * - 호스트가 수동 체크인 처리를 누락해도 시스템이 backstop 으로 처리
 * - 한 번 자동 체크인된 예약은 더 이상 cancel 불가 → 어뷰즈 방어
 *
 * 트레이드오프: 배치가 다운/지연되면 자동 처리 누락 가능. 운영 모니터링 영역.
 */
@Component
class AutoCheckInScheduler(
    private val bookingOrderJpaRepository: BookingOrderJpaRepository,
    private val lineItemJpaRepository: BookingOrderLineItemJpaRepository,
    private val accommodationService: AccommodationService,
    private val checkInUseCase: CheckInUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 5 분 주기로 실행. 체크인 시각이 지난 CONFIRMED 예약을 자동 처리. */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    fun autoCheckInDueOrders() {
        val now = LocalDateTime.now()
        val candidates = runCatching { bookingOrderJpaRepository.findAllByStatus(BookingStatus.CONFIRMED) }
            .getOrElse {
                log.warn("자동 체크인 대상 조회 실패: {}", it.message)
                return
            }

        for (order in candidates) {
            val lineItems = runCatching { lineItemJpaRepository.findByBookingOrderId(order.id) }.getOrNull()
                ?: continue
            val firstLine = lineItems.firstOrNull() ?: continue
            val accommodation = runCatching {
                accommodationService.loadAccommodation(firstLine.accommodationSnapshot.accommodationId)
            }.getOrNull() ?: continue

            val dueAt = LocalDateTime.of(firstLine.checkIn, accommodation.effectiveCheckInTime())
            if (now.isBefore(dueAt)) continue

            runCatching { checkInUseCase.checkIn(order.id) }
                .onFailure { ex ->
                    log.warn("자동 체크인 실패 orderId={}: {}", order.id, ex.message)
                }
                .onSuccess {
                    log.info("자동 체크인 완료 orderId={} dueAt={}", order.id, dueAt)
                }
        }
    }
}
