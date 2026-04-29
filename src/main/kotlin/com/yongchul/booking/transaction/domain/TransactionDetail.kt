package com.yongchul.booking.transaction.domain

import com.yongchul.booking.transaction.domain.vo.LedgerInfo
import com.yongchul.booking.transaction.domain.vo.RefundAmount
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
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
@Table(
    name = "transaction_detail",
    uniqueConstraints = [
        // Week3 신규: 부분취소 멱등성. 같은 요청키는 최대 1회만 저장된다.
        jakarta.persistence.UniqueConstraint(
            name = "uk_transaction_detail_request_key",
            columnNames = ["request_key"],
        ),
    ],
)
class TransactionDetail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionDetailType,

    /**
     * PAYMENT 타입에서만 채워지며, REFUND/PARTIAL_REFUND 타입에서는 null.
     * LedgerInfo 원본 컬럼이 nullable=false 이므로 여기서 nullable=true 로 재정의한다.
     * (@Embedded 필드가 null 일 때 Hibernate 의 not-null 검증을 우회하기 위함)
     */
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "pgTransactionId", column = Column(name = "pg_transaction_id", nullable = true, length = 100)),
        AttributeOverride(name = "approvalNumber",  column = Column(name = "approval_number",  nullable = true, length = 50)),
        AttributeOverride(name = "pgName",          column = Column(name = "pg_name",          nullable = true, length = 50)),
        AttributeOverride(name = "paidAmount.amount",   column = Column(name = "paid_amount",   nullable = true)),
        AttributeOverride(name = "paidAmount.currency", column = Column(name = "paid_currency", nullable = true)),
    )
    val ledgerInfo: LedgerInfo? = null,

    @Embedded
    val refundAmount: RefundAmount? = null,

    /**
     * Week3 신규: 부분취소/환불 멱등성 키.
     * `hash(예약ID + 정렬된 부분취소 날짜)` 형태. unique constraint 로 중복 환불 방어.
     * PARTIAL_REFUND 타입에서만 채워지며, PAYMENT/REFUND 타입에서는 NULL.
     */
    @Column(name = "request_key", nullable = true, length = 128, updatable = false)
    val requestKey: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    init {
        require(type != TransactionDetailType.PAYMENT || ledgerInfo != null) {
            "PAYMENT 타입은 ledgerInfo 필수"
        }
        require(
            (type != TransactionDetailType.REFUND && type != TransactionDetailType.PARTIAL_REFUND) ||
                refundAmount != null
        ) {
            "REFUND/PARTIAL_REFUND 타입은 refundAmount 필수"
        }
        require(type != TransactionDetailType.PARTIAL_REFUND || !requestKey.isNullOrBlank()) {
            "PARTIAL_REFUND 타입은 requestKey 필수"
        }
    }
}
