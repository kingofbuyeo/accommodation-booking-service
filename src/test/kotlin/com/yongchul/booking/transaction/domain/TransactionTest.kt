package com.yongchul.booking.transaction.domain

import com.yongchul.booking.common.Money
import com.yongchul.booking.transaction.domain.vo.LedgerInfo
import com.yongchul.booking.transaction.domain.vo.RefundAmount
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TransactionTest {

    private val ledgerInfo = LedgerInfo(
        pgTransactionId = "PG-001",
        approvalNumber = "APPR-001",
        pgName = "KakaoPay",
        paidAmount = Money.of(300_000),
    )

    private fun paidTransactionWithDetails(): Pair<Transaction, List<TransactionDetail>> {
        val tx = Transaction(bookingOrderId = 1L)
        val paymentDetail = tx.complete(ledgerInfo)
        return tx to listOf(paymentDetail)
    }

    @Test
    fun `PENDING 상태에서 complete 하면 PAID가 되고 PAYMENT detail이 반환된다`() {
        val tx = Transaction(bookingOrderId = 1L)
        val detail = tx.complete(ledgerInfo)
        assertThat(tx.status).isEqualTo(TransactionStatus.PAID)
        assertThat(detail.type).isEqualTo(TransactionDetailType.PAYMENT)
        assertThat(detail.ledgerInfo?.paidAmount).isEqualTo(Money.of(300_000))
    }

    @Test
    fun `PENDING 상태에서 fail 하면 FAILED가 된다`() {
        val tx = Transaction(bookingOrderId = 1L)
        tx.fail()
        assertThat(tx.status).isEqualTo(TransactionStatus.FAILED)
    }

    @Test
    fun `PAID 상태에서 cancel 하면 CANCELLED가 되고 환불 detail이 생성된다`() {
        val (tx, details) = paidTransactionWithDetails()
        val refund = tx.cancel(details, "예약 선점 만료")
        assertThat(tx.status).isEqualTo(TransactionStatus.CANCELLED)
        assertThat(refund.type).isEqualTo(TransactionDetailType.REFUND)
        assertThat(refund.refundAmount?.money).isEqualTo(Money.of(300_000))
    }

    @Test
    fun `PAID 상태에서 cancelWithPenalty 로 페널티 차감 환불 시 FULLY_CANCELLED 로 전이된다`() {
        val (tx, details) = paidTransactionWithDetails()
        val refund = tx.cancelWithPenalty(
            details,
            RefundAmount(money = Money.of(210_000), reason = "고객 취소 — 페널티 30% 차감"),
        )
        assertThat(tx.status).isEqualTo(TransactionStatus.FULLY_CANCELLED)
        assertThat(refund.type).isEqualTo(TransactionDetailType.REFUND)
        assertThat(refund.refundAmount?.money).isEqualTo(Money.of(210_000))
    }

    @Test
    fun `cancelWithPenalty 로 전액 환불(페널티 0) 되어도 FULLY_CANCELLED 로 전이된다`() {
        val (tx, details) = paidTransactionWithDetails()
        tx.cancelWithPenalty(details, RefundAmount(money = Money.of(300_000), reason = "전액 환불"))
        assertThat(tx.status).isEqualTo(TransactionStatus.FULLY_CANCELLED)
    }

    @Test
    fun `cancelWithPenalty 환불 금액이 결제 금액을 초과하면 예외가 발생한다`() {
        val (tx, details) = paidTransactionWithDetails()
        assertThatThrownBy {
            tx.cancelWithPenalty(details, RefundAmount(money = Money.of(400_000), reason = "초과 환불"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("환불 금액이 결제 금액을 초과합니다")
    }

    @Test
    fun `cancelWithPenalty 는 PAID 가 아닌 상태에서는 실행할 수 없다`() {
        val (tx, details) = paidTransactionWithDetails()
        tx.cancelWithPenalty(details, RefundAmount(money = Money.of(300_000), reason = "1차 취소"))
        assertThatThrownBy {
            tx.cancelWithPenalty(details, RefundAmount(money = Money.of(100_000), reason = "2차 시도"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("PAID 상태에서만")
    }
}