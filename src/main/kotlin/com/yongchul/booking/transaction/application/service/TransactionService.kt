package com.yongchul.booking.transaction.application.service

import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.adapter.out.persistence.TransactionDetailJpaRepository
import com.yongchul.booking.transaction.adapter.out.persistence.TransactionJpaRepository
import com.yongchul.booking.transaction.application.port.`in`.InitiateTransactionUseCase
import com.yongchul.booking.transaction.application.port.`in`.ProcessTransactionUseCase
import com.yongchul.booking.transaction.application.port.`in`.RefundTransactionUseCase
import com.yongchul.booking.transaction.domain.Transaction
import com.yongchul.booking.transaction.domain.event.PartialRefundProcessedEvent
import com.yongchul.booking.transaction.domain.event.TransactionCancelledEvent
import com.yongchul.booking.transaction.domain.event.TransactionCancelReason
import com.yongchul.booking.transaction.domain.event.TransactionCompletedEvent
import com.yongchul.booking.transaction.domain.event.TransactionFailedEvent
import com.yongchul.booking.transaction.domain.event.TransactionFullyRefundedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TransactionService(
    private val transactionJpaRepository: TransactionJpaRepository,
    private val transactionDetailJpaRepository: TransactionDetailJpaRepository,
    private val eventPublisher: DomainEventPublisher,
) : InitiateTransactionUseCase, ProcessTransactionUseCase, RefundTransactionUseCase {

    fun loadTransaction(transactionId: Long): Transaction =
        transactionJpaRepository.findById(transactionId).orElseThrow {
            NoSuchElementException("거래를 찾을 수 없습니다: id=$transactionId")
        }

    @Transactional
    override fun initiate(command: InitiateTransactionUseCase.InitiateCommand): Transaction =
        transactionJpaRepository.save(Transaction(bookingOrderId = command.bookingOrderId))

    @Transactional
    override fun complete(command: ProcessTransactionUseCase.CompleteCommand) {
        val transaction = loadTransaction(command.transactionId)
        val detail = transaction.complete(command.ledgerInfo)
        transactionDetailJpaRepository.save(detail)
        eventPublisher.publish(
            KafkaTopics.TRANSACTION_EVENTS,
            TransactionCompletedEvent(
                transactionId = transaction.id.toString(),
                bookingOrderId = transaction.bookingOrderId.toString(),
            )
        )
    }

    @Transactional
    override fun fail(transactionId: Long) {
        val transaction = loadTransaction(transactionId)
        transaction.fail()
        eventPublisher.publish(
            KafkaTopics.TRANSACTION_EVENTS,
            TransactionFailedEvent(
                transactionId = transaction.id.toString(),
                bookingOrderId = transaction.bookingOrderId.toString(),
            )
        )
    }

    @Transactional
    override fun cancel(command: ProcessTransactionUseCase.CancelCommand) {
        val transaction = loadTransaction(command.transactionId)
        val currentDetails = transactionDetailJpaRepository.findByTransactionId(command.transactionId)
        val detail = transaction.cancel(currentDetails, command.reason)
        transactionDetailJpaRepository.save(detail)
        eventPublisher.publish(
            KafkaTopics.TRANSACTION_EVENTS,
            TransactionCancelledEvent(
                transactionId = transaction.id.toString(),
                bookingOrderId = transaction.bookingOrderId.toString(),
                reason = TransactionCancelReason.PREEMPTION_EXPIRED,
            )
        )
    }

    @Transactional
    override fun partialRefund(command: RefundTransactionUseCase.PartialRefundCommand) {
        val transaction = loadTransaction(command.transactionId)
        val currentDetails = transactionDetailJpaRepository.findByTransactionId(command.transactionId)
        val detail = transaction.partialRefund(currentDetails, command.refundAmount)
        transactionDetailJpaRepository.save(detail)

        if (transaction.status == com.yongchul.booking.transaction.domain.TransactionStatus.FULLY_CANCELLED) {
            eventPublisher.publish(
                KafkaTopics.TRANSACTION_EVENTS,
                TransactionFullyRefundedEvent(
                    transactionId = transaction.id.toString(),
                    bookingOrderId = transaction.bookingOrderId.toString(),
                )
            )
        } else {
            eventPublisher.publish(
                KafkaTopics.TRANSACTION_EVENTS,
                PartialRefundProcessedEvent(
                    transactionId = transaction.id.toString(),
                    bookingOrderId = transaction.bookingOrderId.toString(),
                    refundAmountValue = command.refundAmount.money.amount.toLong(),
                    currency = command.refundAmount.money.currency,
                )
            )
        }
    }
}
