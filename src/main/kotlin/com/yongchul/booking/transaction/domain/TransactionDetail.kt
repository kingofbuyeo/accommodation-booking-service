package com.yongchul.booking.transaction.domain

import com.yongchul.booking.transaction.domain.vo.LedgerInfo
import com.yongchul.booking.transaction.domain.vo.RefundAmount
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 결제/환불 원장 Entity — append-only, 수정 불가
 *
 * 불변조건:
 * - 생성 이후 수정 불가 (원장 이력 보존)
 * - REFUND 타입이면 refundAmount 필수
 * - PAYMENT 타입이면 ledgerInfo 필수
 */
@Entity
@Table(name = "transaction_detail")
class TransactionDetail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionDetailType,

    @Embedded
    val ledgerInfo: LedgerInfo? = null,

    @Embedded
    val refundAmount: RefundAmount? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    init {
        require(type != TransactionDetailType.PAYMENT || ledgerInfo != null) {
            "PAYMENT 타입은 ledgerInfo 필수"
        }
        require(type != TransactionDetailType.REFUND || refundAmount != null) {
            "REFUND 타입은 refundAmount 필수"
        }
    }
}
