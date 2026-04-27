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
 * PENDING → PAID → FULLY_CANCELLED  (고객 취소 요청 — 페널티 차감 후 잔액 환불, 한 번으로 종료)
 *         → FAILED                  (결제 실패 — 재시도는 신규 Transaction 생성)
 *         → CANCELLED               (Booking EXPIRED 확인 후 시스템 자동 환불 처리)
 *
 * 불변조건:
 * - TransactionDetail은 append-only (수정 불가)
 * - 고객 요청 취소는 단 한 번의 환불 row를 남기고 즉시 FULLY_CANCELLED로 전이
 *   (페널티 잔액은 호스트 몫으로 남고 더 이상 환불 발생하지 않음)
 *
 * 숙박 도메인 메모:
 * - 날짜 단위 부분 취소는 허용하지 않음 — 취소는 예약 전체 단위
 * - "부분 취소" 용어는 페널티 차감 환불 케이스를 가리킴 (도메인 이벤트는 FullyRefunded 단일)
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

    /**
     * 고객 요청 취소 — 페널티 차감 후 잔액을 환불한다.
     *
     * refundAmount 가 0 이상 결제액 이하임을 검증한 뒤 REFUND detail 1건을 추가하고
     * 상태를 즉시 FULLY_CANCELLED 로 전이한다. 페널티 잔액(= paidAmount - refundAmount)은
     * 호스트 몫으로 남아 더 이상 환불 대상이 아니다.
     */
    fun cancelWithPenalty(currentDetails: List<TransactionDetail>, refundAmount: RefundAmount): TransactionDetail {
        require(status == TransactionStatus.PAID) {
            "PAID 상태에서만 고객 요청 취소를 처리할 수 있습니다. 현재 상태: $status"
        }
        val paid = computePaidAmount(currentDetails)
        require(paid.amount.signum() > 0) {
            "결제 금액이 0인 Transaction은 환불할 수 없습니다."
        }
        require(refundAmount.money.amount.signum() >= 0) {
            "환불 금액은 0 이상이어야 합니다. 요청: ${refundAmount.money}"
        }
        require(refundAmount.money.amount <= paid.amount) {
            "환불 금액이 결제 금액을 초과합니다. 결제: $paid, 요청: ${refundAmount.money}"
        }
        status = TransactionStatus.FULLY_CANCELLED
        updatedAt = LocalDateTime.now()
        return TransactionDetail(
            transactionId = id,
            type = TransactionDetailType.REFUND,
            refundAmount = refundAmount,
        )
    }

    private fun computePaidAmount(details: List<TransactionDetail>): Money =
        details.filter { it.type == TransactionDetailType.PAYMENT }
            .mapNotNull { it.ledgerInfo?.paidAmount }
            .fold(Money.ZERO) { acc, m -> acc + m }
}
