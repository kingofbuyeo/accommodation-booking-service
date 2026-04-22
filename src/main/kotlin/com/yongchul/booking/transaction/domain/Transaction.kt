package com.yongchul.booking.transaction.domain

import com.yongchul.booking.common.Money
import com.yongchul.booking.transaction.domain.vo.LedgerInfo
import com.yongchul.booking.transaction.domain.vo.RefundAmount
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 결제 Aggregate Root
 *
 * 상태 흐름:
 * PENDING → PAID → PARTIAL_CANCELLED → FULLY_CANCELLED
 *         → FAILED  (결제 실패 — 재시도는 신규 Transaction 생성)
 *         → CANCELLED (Booking EXPIRED 확인 후 결제 취소)
 *
 * 불변조건:
 * - TransactionDetail은 append-only (수정 불가)
 * - 잔여 결제 금액이 0이 되는 시점에 FULLY_CANCELLED 전이
 */
@Entity
@Table(name = "transaction")
class Transaction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "booking_order_id", nullable = false)
    val bookingOrderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TransactionStatus = TransactionStatus.PENDING,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun complete(ledgerInfo: LedgerInfo): TransactionDetail {
        require(status == TransactionStatus.PENDING) {
            "PENDING 상태에서만 결제 완료 처리할 수 있습니다. 현재 상태: $status"
        }
        status = TransactionStatus.PAID
        updatedAt = LocalDateTime.now()
        return TransactionDetail(transactionId = id, type = TransactionDetailType.PAYMENT, ledgerInfo = ledgerInfo)
    }

    fun fail() {
        require(status == TransactionStatus.PENDING) {
            "PENDING 상태에서만 실패 처리할 수 있습니다. 현재 상태: $status"
        }
        status = TransactionStatus.FAILED
        updatedAt = LocalDateTime.now()
    }

    fun cancel(currentDetails: List<TransactionDetail>, reason: String): TransactionDetail {
        require(status == TransactionStatus.PAID) {
            "PAID 상태에서만 취소 처리할 수 있습니다. 현재 상태: $status"
        }
        val paid = computePaidAmount(currentDetails)
        status = TransactionStatus.CANCELLED
        updatedAt = LocalDateTime.now()
        return TransactionDetail(
            transactionId = id,
            type = TransactionDetailType.REFUND,
            refundAmount = RefundAmount(money = paid, reason = reason),
        )
    }

    fun partialRefund(currentDetails: List<TransactionDetail>, refundAmount: RefundAmount): TransactionDetail {
        require(status == TransactionStatus.PAID || status == TransactionStatus.PARTIAL_CANCELLED) {
            "PAID 또는 PARTIAL_CANCELLED 상태에서만 부분 환불할 수 있습니다. 현재 상태: $status"
        }
        val remaining = computeRemainingAmount(currentDetails)
        require(refundAmount.money.amount <= remaining.amount) {
            "환불 금액이 잔여 결제 금액을 초과합니다. 잔여: $remaining, 요청: ${refundAmount.money}"
        }
        val detail = TransactionDetail(transactionId = id, type = TransactionDetailType.REFUND, refundAmount = refundAmount)
        val newRemaining = remaining - refundAmount.money
        status = if (newRemaining.amount.signum() == 0) TransactionStatus.FULLY_CANCELLED
                 else TransactionStatus.PARTIAL_CANCELLED
        updatedAt = LocalDateTime.now()
        return detail
    }

    private fun computePaidAmount(details: List<TransactionDetail>): Money =
        details.filter { it.type == TransactionDetailType.PAYMENT }
            .mapNotNull { it.ledgerInfo?.paidAmount }
            .fold(Money.ZERO) { acc, m -> acc + m }

    private fun computeRefundedAmount(details: List<TransactionDetail>): Money =
        details.filter { it.type == TransactionDetailType.REFUND }
            .mapNotNull { it.refundAmount?.money }
            .fold(Money.ZERO) { acc, m -> acc + m }

    private fun computeRemainingAmount(details: List<TransactionDetail>): Money =
        computePaidAmount(details) - computeRefundedAmount(details)
}
