package com.yongchul.booking.transaction.domain.vo

import com.yongchul.booking.common.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded

@Embeddable
data class RefundAmount(
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "refund_amount")),
        AttributeOverride(name = "currency", column = Column(name = "refund_currency")),
    )
    val money: Money,

    @Column(name = "refund_reason", length = 200)
    val reason: String,
)
