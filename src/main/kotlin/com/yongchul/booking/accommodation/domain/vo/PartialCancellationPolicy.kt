package com.yongchul.booking.accommodation.domain.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 숙소별 부분취소 정책 (Week3 신규).
 *
 * 호스트가 숙소/방별로 설정하며, NULL 인 경우 도메인 규칙상 부분취소 불가로 해석한다.
 *
 * - [enabled]: 부분취소 허용 여부
 * - [deadlineDaysBeforeCheckIn]: 원본 체크인 D-N 일까지 부분취소 요청 가능. (e.g. 7 → 체크인 7일 전까지)
 * - [penaltyRatio]: 부분취소 시 환불 비율 (0.0 ~ 1.0). 1.0 = 페널티 0%, 0.5 = 페널티 50%.
 *
 * **결정 카드 #8**: 정책 변경의 위험은 호스트가 부담 — 요청 시점 정책을 적용한다.
 */
@Embeddable
data class PartialCancellationPolicy(
    @Column(name = "partial_cancel_enabled")
    val enabled: Boolean? = null,

    @Column(name = "partial_cancel_deadline_days")
    val deadlineDaysBeforeCheckIn: Int? = null,

    @Column(name = "partial_cancel_penalty_ratio", precision = 4, scale = 3)
    val penaltyRatio: BigDecimal? = null,
) {
    init {
        deadlineDaysBeforeCheckIn?.let {
            require(it >= 0) { "deadlineDaysBeforeCheckIn 은 0 이상이어야 합니다. 입력: $it" }
        }
        penaltyRatio?.let {
            require(it in MIN_RATIO..MAX_RATIO) {
                "penaltyRatio 는 $MIN_RATIO ~ $MAX_RATIO 사이여야 합니다. 입력: $it"
            }
        }
    }

    val effectiveEnabled: Boolean
        get() = enabled == true

    val effectiveDeadlineDays: Int
        get() = deadlineDaysBeforeCheckIn ?: DEFAULT_DEADLINE_DAYS

    val effectivePenaltyRatio: BigDecimal
        get() = penaltyRatio ?: DEFAULT_PENALTY_RATIO

    /**
     * 요청 시점이 부분취소 가능 기간 안에 있는지 검사한다.
     * `requestedAt` 이 `checkInDate - N` 이전이면 가능, 이후면 거절.
     */
    fun isWithinDeadline(checkInDate: LocalDate, requestedAt: LocalDate = LocalDate.now()): Boolean {
        val days = ChronoUnit.DAYS.between(requestedAt, checkInDate)
        return days >= effectiveDeadlineDays
    }

    /**
     * 환불 비율 (= 1 - 페널티 비율 X 의 직접 표현).
     * 결제 금액에 곱해 환불 금액을 산출한다.
     */
    fun resolveRefundRatio(): BigDecimal = effectivePenaltyRatio

    companion object {
        const val DEFAULT_DEADLINE_DAYS: Int = 7
        val DEFAULT_PENALTY_RATIO: BigDecimal = BigDecimal("0.9")
        val MIN_RATIO: BigDecimal = BigDecimal("0.0")
        val MAX_RATIO: BigDecimal = BigDecimal("1.0")
    }
}
