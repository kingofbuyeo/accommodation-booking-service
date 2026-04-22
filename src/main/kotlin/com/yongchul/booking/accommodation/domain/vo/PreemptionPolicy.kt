package com.yongchul.booking.accommodation.domain.vo

import jakarta.persistence.Embeddable
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Embeddable

/**
 * 숙소별 선점 TTL 정책.
 * 체크인 일자와 예약 시점의 차이(리드타임)에 따라 TTL을 차등 적용한다.
 *
 * 리드타임이 짧을수록 TTL 단축 — 공실 기회 손실 최소화
 * 리드타임이 길수록 TTL 연장 — 고객 결제 여유 시간 확보
 */
data class PreemptionPolicy(
    val shortLeadTimeDays: Int = 3,
    val shortLeadTimeTtlMinutes: Long = 20,
    val longLeadTimeDays: Int = 30,
    val longLeadTimeTtlMinutes: Long = 120,
    val defaultTtlMinutes: Long = 60,
) {
    fun calculateTtl(checkInDate: LocalDate, today: LocalDate = LocalDate.now()): Duration {
        val daysUntilCheckIn = ChronoUnit.DAYS.between(today, checkInDate)
        return when {
            daysUntilCheckIn <= shortLeadTimeDays -> Duration.ofMinutes(shortLeadTimeTtlMinutes)
            daysUntilCheckIn >= longLeadTimeDays  -> Duration.ofMinutes(longLeadTimeTtlMinutes)
            else                                  -> Duration.ofMinutes(defaultTtlMinutes)
        }
    }
}
