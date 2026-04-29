package com.yongchul.booking.transaction.application.service

import com.yongchul.booking.common.Money
import com.yongchul.booking.transaction.adapter.out.persistence.TransactionDetailJpaRepository
import com.yongchul.booking.transaction.adapter.out.persistence.TransactionJpaRepository
import com.yongchul.booking.transaction.application.port.`in`.FindTransactionByBookingOrderUseCase
import com.yongchul.booking.transaction.application.port.`in`.InitiateTransactionUseCase
import com.yongchul.booking.transaction.application.port.`in`.ProcessTransactionUseCase
import com.yongchul.booking.transaction.application.port.`in`.RefundTransactionUseCase
import com.yongchul.booking.transaction.domain.Transaction
import com.yongchul.booking.transaction.domain.TransactionDetail
import com.yongchul.booking.transaction.domain.TransactionDetailType
import com.yongchul.booking.transaction.domain.TransactionStatus
import com.yongchul.booking.transaction.domain.event.TransactionCancelReason
import com.yongchul.booking.transaction.domain.event.TransactionCancelledEvent
import com.yongchul.booking.transaction.domain.event.TransactionCompletedEvent
import com.yongchul.booking.transaction.domain.event.TransactionFailedEvent
import com.yongchul.booking.transaction.domain.event.TransactionFullyRefundedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Transaction DB 처리 전용 서비스.
 *
 * 모든 메서드가 `@Transactional` 경계 안에서 실행되며, 메서드 종료 시점에 commit 된다.
 * Kafka 이벤트 발행은 절대 이 서비스에서 수행하지 않으며, 발행할 이벤트 객체만 반환한다.
 *
 * Q3-A 결정: dual-write 방어를 위해 DB commit 과 Kafka publish 를 분리한다.
 * 호출자([TransactionService] 오케스트레이터) 는 메서드 호출 종료 직후 commit 된 상태에서
 * 반환받은 이벤트를 발행한다.
 */
@Service
@Transactional(readOnly = true)
class TransactionDataService(
    private val transactionJpaRepository: TransactionJpaRepository,
    private val transactionDetailJpaRepository: TransactionDetailJpaRepository,
) {

    /** 읽기 전용 — preview/조회용. 락 없음. */
    fun loadTransaction(transactionId: Long): Transaction =
        transactionJpaRepository.findById(transactionId).orElseThrow {
            NoSuchElementException("거래를 찾을 수 없습니다: id=$transactionId")
        }

    /**
     * 쓰기 경로 전용 — Transaction 상태 전이 시 사용.
     * PESSIMISTIC_WRITE 로 동시 mutation race 차단.
     * 반드시 `@Transactional` 컨텍스트 안에서만 호출할 것.
     */
    fun loadTransactionForUpdate(transactionId: Long): Transaction =
        transactionJpaRepository.findByIdForUpdate(transactionId).orElseThrow {
            NoSuchElementException("거래를 찾을 수 없습니다: id=$transactionId")
        }

    @Transactional
    fun initiate(command: InitiateTransactionUseCase.InitiateCommand): Transaction =
        transactionJpaRepository.save(Transaction(bookingOrderId = command.bookingOrderId))

    @Transactional
    fun completeAndPersist(command: ProcessTransactionUseCase.CompleteCommand): TransactionCompletedEvent {
        val transaction = loadTransactionForUpdate(command.transactionId)
        val detail = transaction.complete(command.ledgerInfo)
        transactionDetailJpaRepository.save(detail)
        return TransactionCompletedEvent(
            transactionId = transaction.id.toString(),
            bookingOrderId = transaction.bookingOrderId.toString(),
        )
    }

    @Transactional
    fun failAndPersist(transactionId: Long): TransactionFailedEvent {
        val transaction = loadTransactionForUpdate(transactionId)
        transaction.fail()
        return TransactionFailedEvent(
            transactionId = transaction.id.toString(),
            bookingOrderId = transaction.bookingOrderId.toString(),
        )
    }

    @Transactional
    fun cancelAndPersist(command: ProcessTransactionUseCase.CancelCommand): TransactionCancelledEvent {
        val transaction = loadTransactionForUpdate(command.transactionId)
        val currentDetails = transactionDetailJpaRepository.findByTransactionId(command.transactionId)
        val detail = transaction.cancel(currentDetails, command.reason)
        transactionDetailJpaRepository.save(detail)
        return TransactionCancelledEvent(
            transactionId = transaction.id.toString(),
            bookingOrderId = transaction.bookingOrderId.toString(),
            reason = TransactionCancelReason.PREEMPTION_EXPIRED,
        )
    }

    @Transactional
    fun cancelWithPenaltyAndPersist(
        command: RefundTransactionUseCase.CancelWithPenaltyCommand,
    ): TransactionFullyRefundedEvent {
        val transaction = loadTransactionForUpdate(command.transactionId)
        val currentDetails = transactionDetailJpaRepository.findByTransactionId(command.transactionId)
        val detail = transaction.cancelWithPenalty(currentDetails, command.refundAmount)
        transactionDetailJpaRepository.save(detail)
        return TransactionFullyRefundedEvent(
            transactionId = transaction.id.toString(),
            bookingOrderId = transaction.bookingOrderId.toString(),
        )
    }

    fun findLatestPaidByBookingOrderId(
        bookingOrderId: Long,
    ): FindTransactionByBookingOrderUseCase.TransactionSummary? {
        val transactions = transactionJpaRepository.findAllByBookingOrderId(bookingOrderId)
        if (transactions.isEmpty()) return null
        val paid = transactions.firstOrNull { it.status == TransactionStatus.PAID } ?: return null
        val paidAmount = sumPaidAmount(
            transactionDetailJpaRepository.findByTransactionId(paid.id)
        )
        return FindTransactionByBookingOrderUseCase.TransactionSummary(
            transactionId = paid.id,
            bookingOrderId = paid.bookingOrderId,
            status = paid.status,
            paidAmount = paidAmount,
        )
    }

    private fun sumPaidAmount(details: List<TransactionDetail>): Money =
        details.filter { it.type == TransactionDetailType.PAYMENT }
            .mapNotNull { it.ledgerInfo?.paidAmount }
            .fold(Money.ZERO) { acc, m -> acc + m }
}
