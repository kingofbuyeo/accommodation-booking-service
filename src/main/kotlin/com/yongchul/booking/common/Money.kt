package com.yongchul.booking.common

import jakarta.persistence.Embeddable
import java.math.BigDecimal

@Embeddable
data class Money(
    val amount: BigDecimal,
    val currency: String = "KRW",
) {
    init {
        require(amount >= BigDecimal.ZERO) { "금액은 0 이상이어야 합니다." }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "통화 단위가 다릅니다: $currency vs ${other.currency}" }
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "통화 단위가 다릅니다: $currency vs ${other.currency}" }
        return Money(amount - other.amount, currency)
    }

    operator fun times(multiplier: Int): Money = Money(amount * multiplier.toBigDecimal(), currency)

    companion object {
        fun of(amount: Long, currency: String = "KRW") = Money(amount.toBigDecimal(), currency)
        val ZERO = Money(BigDecimal.ZERO)
    }
}
