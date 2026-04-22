package com.yongchul.booking.transaction.domain.vo

import com.yongchul.booking.common.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded

@Embeddable
data class LedgerInfo(
    @Column(name = "pg_transaction_id", nullable = false, length = 100)
    val pgTransactionId: String,

    @Column(name = "approval_number", nullable = false, length = 50)
    val approvalNumber: String,

    @Column(name = "pg_name", nullable = false, length = 50)
    val pgName: String,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "paid_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "paid_currency", nullable = false)),
    )
    val paidAmount: Money,
)
