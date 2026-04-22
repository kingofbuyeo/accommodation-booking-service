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

    private fun paidTransaction(): Transaction {
        val tx = Transaction(bookingOrderId = 1L)
        tx.complete(ledgerInfo)
        return tx
    }

    @Test
    fun `PENDING 상태에서 complete 하면 PAID가 되고 PAYMENT detail이 추가된다`() {
        val tx = Transaction(bookingOrderId = 1L)
        tx.complete(ledgerInfo)
        assertThat(tx.status).isEqualTo(TransactionStatus.PAID)
        assertThat(tx.details).hasSize(1)
        assertThat(tx.details[0].type).isEqualTo(TransactionDetailType.PAYMENT)
        assertThat(tx.paidAmount).isEqualTo(Money.of(300_000))
    }

    @Test
    fun `PENDING 상태에서 fail 하면 FAILED가 된다`() {
        val tx = Transaction(bookingOrderId = 1L)
        tx.fail()
        assertThat(tx.status).isEqualTo(TransactionStatus.FAILED)
    }

    @Test
    fun `PAID 상태에서 cancel 하면 CANCELLED가 되고 환불 detail이 추가된다`() {
        val tx = paidTransaction()
        tx.cancel("예약 선점 만료")
        assertThat(tx.status).isEqualTo(TransactionStatus.CANCELLED)
        assertThat(tx.details).hasSize(2)
        assertThat(tx.details[1].type).isEqualTo(TransactionDetailType.REFUND)
    }

    @Test
    fun `부분 환불 후 잔여 금액이 남으면 PARTIAL_CANCELLED가 된다`() {
        val tx = paidTransaction()
        tx.partialRefund(RefundAmount(money = Money.of(100_000), reason = "1박 취소"))
        assertThat(tx.status).isEqualTo(TransactionStatus.PARTIAL_CANCELLED)
        assertThat(tx.remainingAmount).isEqualTo(Money.of(200_000))
        assertThat(tx.details).hasSize(2)
    }

    @Test
    fun `부분 환불 후 잔여 금액이 0이면 FULLY_CANCELLED가 된다`() {
        val tx = paidTransaction()
        tx.partialRefund(RefundAmount(money = Money.of(300_000), reason = "전액 환불"))
        assertThat(tx.status).isEqualTo(TransactionStatus.FULLY_CANCELLED)
        assertThat(tx.remainingAmount).isEqualTo(Money.ZERO)
    }

    @Test
    fun `환불 금액이 잔여 결제 금액을 초과하면 예외가 발생한다`() {
        val tx = paidTransaction()
        assertThatThrownBy {
            tx.partialRefund(RefundAmount(money = Money.of(400_000), reason = "초과 환불"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("환불 금액이 잔여 결제 금액을 초과합니다")
    }

    @Test
    fun `TransactionDetail은 append-only — PAID 이후 detail 수는 증가만 한다`() {
        val tx = paidTransaction()
        val sizeAfterPayment = tx.details.size
        tx.partialRefund(RefundAmount(money = Money.of(100_000), reason = "1박 취소"))
        assertThat(tx.details.size).isGreaterThan(sizeAfterPayment)
    }
}
