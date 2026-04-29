package com.yongchul.booking.accommodation.domain.vo

import com.yongchul.booking.accommodation.adapter.out.persistence.CancellationPenaltyTiersConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 숙소 운영 정책 (선점 TTL + 취소 페널티).
 *
 * 기존 PreemptionPolicy 를 확장/리네이밍 한 것으로, 호스트가 숙소/방별로 운영 규칙을 설정한다.
 *
 * - 선점: 체크인까지 리드타임에 따라 TTL 을 차등 적용
 * - 취소 페널티: 체크인까지 남은 일수 구간별 환불 비율 (계단형, 최대 페널티 50%)
 *
 * 필드는 nullable 로 선언되어 있다. 이유:
 *   ddl-auto=update 환경에서 컬럼이 나중에 추가된 경우 기존 row 에 NULL 이 들어가 있을 수 있어
 *   Hibernate 가 primitive 필드에 null 을 set 하려다 예외가 나는 것을 방지하기 위함이다.
 *   실제 동작 시점에는 [DEFAULT_*] 기본값으로 fallback 한다.
 */
@Embeddable
data class AccommodationOperationPolicy(
    val shortLeadTimeDays: Int? = DEFAULT_SHORT_LEAD_TIME_DAYS,
    val shortLeadTimeTtlMinutes: Long? = DEFAULT_SHORT_LEAD_TIME_TTL_MINUTES,
    val longLeadTimeDays: Int? = DEFAULT_LONG_LEAD_TIME_DAYS,
    val longLeadTimeTtlMinutes: Long? = DEFAULT_LONG_LEAD_TIME_TTL_MINUTES,
    val defaultTtlMinutes: Long? = DEFAULT_TTL_MINUTES,

    @Convert(converter = CancellationPenaltyTiersConverter::class)
    @Column(name = "cancellation_penalty_tiers", columnDefinition = "TEXT")
    val cancellationPenaltyTiers: List<CancellationPenaltyTier>? = DEFAULT_TIERS,
) {
    val effectiveShortLeadTimeDays: Int
        get() = shortLeadTimeDays ?: DEFAULT_SHORT_LEAD_TIME_DAYS

    val effectiveShortLeadTimeTtlMinutes: Long
        get() = shortLeadTimeTtlMinutes ?: DEFAULT_SHORT_LEAD_TIME_TTL_MINUTES

    val effectiveLongLeadTimeDays: Int
        get() = longLeadTimeDays ?: DEFAULT_LONG_LEAD_TIME_DAYS

    val effectiveLongLeadTimeTtlMinutes: Long
        get() = longLeadTimeTtlMinutes ?: DEFAULT_LONG_LEAD_TIME_TTL_MINUTES

    val effectiveDefaultTtlMinutes: Long
        get() = defaultTtlMinutes ?: DEFAULT_TTL_MINUTES

    val effectiveCancellationPenaltyTiers: List<CancellationPenaltyTier>
        get() = cancellationPenaltyTiers?.takeIf { it.isNotEmpty() } ?: DEFAULT_TIERS

    fun calculateTtl(checkInDate: LocalDate, today: LocalDate = LocalDate.now()): Duration {
        val daysUntilCheckIn = ChronoUnit.DAYS.between(today, checkInDate)
        return when {
            daysUntilCheckIn <= effectiveShortLeadTimeDays -> Duration.ofMinutes(effectiveShortLeadTimeTtlMinutes)
            daysUntilCheckIn >= effectiveLongLeadTimeDays  -> Duration.ofMinutes(effectiveLongLeadTimeTtlMinutes)
            else                                           -> Duration.ofMinutes(effectiveDefaultTtlMinutes)
        }
    }

    /**
     * 취소 요청 시각과 체크인 일자의 차이를 기준으로 매칭되는 페널티 구간의 refundRatio 를 반환한다.
     * 매칭 구간이 없으면 최소 비율(0.5)을 적용해 50% 환불을 보장한다.
     *
     * 환불 금액(`paidAmount × ratio`) 계산 책임은 호출자(예약 컨텍스트)에 있으며,
     * 이 메서드는 비율만 반환한다.
     */
    fun resolveRefundRatio(
        checkInDate: LocalDate,
        requestedAt: LocalDate = LocalDate.now(),
    ): BigDecimal {
        val daysUntilCheckIn = ChronoUnit.DAYS.between(requestedAt, checkInDate).toInt()
        val matched = effectiveCancellationPenaltyTiers
            .filter { daysUntilCheckIn >= it.minDaysToCheckIn }
            .maxByOrNull { it.minDaysToCheckIn }
        return matched?.refundRatio ?: CancellationPenaltyTier.MIN_RATIO
    }

    companion object {
        const val DEFAULT_SHORT_LEAD_TIME_DAYS: Int = 3
        const val DEFAULT_SHORT_LEAD_TIME_TTL_MINUTES: Long = 20
        const val DEFAULT_LONG_LEAD_TIME_DAYS: Int = 30
        const val DEFAULT_LONG_LEAD_TIME_TTL_MINUTES: Long = 120
        const val DEFAULT_TTL_MINUTES: Long = 60

        val DEFAULT_TIERS: List<CancellationPenaltyTier> = listOf(
            CancellationPenaltyTier(minDaysToCheckIn = 7, refundRatio = BigDecimal("1.0")),
            CancellationPenaltyTier(minDaysToCheckIn = 3, refundRatio = BigDecimal("0.7")),
            CancellationPenaltyTier(minDaysToCheckIn = 0, refundRatio = BigDecimal("0.5")),
        )
    }
}
