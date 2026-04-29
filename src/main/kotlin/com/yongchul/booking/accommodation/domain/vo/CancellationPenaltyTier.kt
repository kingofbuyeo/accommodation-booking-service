package com.yongchul.booking.accommodation.domain.vo

import java.math.BigDecimal

/**
 * 취소 페널티 단일 구간.
 *
 * 체크인까지 남은 일수가 [minDaysToCheckIn] 이상이면 이 구간의 [refundRatio] 를 적용한다.
 * [refundRatio] 는 0.5 ~ 1.0 사이(= 페널티 0% ~ 50%).
 */
data class CancellationPenaltyTier(
    val minDaysToCheckIn: Int,
    val refundRatio: BigDecimal,
) {
    init {
        require(minDaysToCheckIn >= 0) {
            "minDaysToCheckIn 은 0 이상이어야 합니다. 입력: $minDaysToCheckIn"
        }
        require(refundRatio in MIN_RATIO..MAX_RATIO) {
            "refundRatio 는 $MIN_RATIO ~ $MAX_RATIO 사이여야 합니다 (페널티 최대 50%). 입력: $refundRatio"
        }
    }

    companion object {
        val MIN_RATIO: BigDecimal = BigDecimal("0.5")
        val MAX_RATIO: BigDecimal = BigDecimal("1.0")
    }
}