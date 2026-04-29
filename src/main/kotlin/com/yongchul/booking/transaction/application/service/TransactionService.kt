package com.yongchul.booking.transaction.application.service

import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.application.port.`in`.FindTransactionByBookingOrderUseCase
import com.yongchul.booking.transaction.application.port.`in`.InitiateTransactionUseCase
import com.yongchul.booking.transaction.application.port.`in`.ProcessTransactionUseCase
import com.yongchul.booking.transaction.application.port.`in`.RefundTransactionUseCase
import com.yongchul.booking.transaction.domain.Transaction
import org.springframework.stereotype.Service

/**
 * Transaction 도메인 오케스트레이터.
 *
 * Q3-A 결정에 따라 DB 처리([TransactionDataService]) 와 Kafka 이벤트 발행([DomainEventPublisher]) 을
 * 명시적으로 분리해 호출한다. **이 클래스에는 의도적으로 `@Transactional` 을 부착하지 않는다.**
 * 이유: 부착 시 자식 호출이 inner-tx 로 합쳐져 commit 이 오케스트레이터 종료 시점이 되고,
 *       Kafka publish 가 commit 이전에 실행되는 dual-write 문제가 재발한다.
 *
 * 호출 순서: dataService.X(...) 종료 → 해당 메서드의 `@Transactional` commit 완료
 *          → eventPublisher.publish(...) 로 Kafka 발행.
 */
@Service
class TransactionService(
    private val dataService: TransactionDataService,
    private val eventPublisher: DomainEventPublisher,
) : InitiateTransactionUseCase,
    ProcessTransactionUseCase,
    RefundTransactionUseCase,
    FindTransactionByBookingOrderUseCase {

    override fun initiate(command: InitiateTransactionUseCase.InitiateCommand): Transaction =
        dataService.initiate(command)

    override fun complete(command: ProcessTransactionUseCase.CompleteCommand) {
        val event = dataService.completeAndPersist(command)
        eventPublisher.publish(KafkaTopics.TRANSACTION_EVENTS, event)
    }

    override fun fail(transactionId: Long) {
        val event = dataService.failAndPersist(transactionId)
        eventPublisher.publish(KafkaTopics.TRANSACTION_EVENTS, event)
    }

    override fun cancel(command: ProcessTransactionUseCase.CancelCommand) {
        val event = dataService.cancelAndPersist(command)
        eventPublisher.publish(KafkaTopics.TRANSACTION_EVENTS, event)
    }

    override fun cancelWithPenalty(command: RefundTransactionUseCase.CancelWithPenaltyCommand) {
        val event = dataService.cancelWithPenaltyAndPersist(command)
        eventPublisher.publish(KafkaTopics.TRANSACTION_EVENTS, event)
    }

    /**
     * Week3 신규: 부분취소 환불.
     * 결제 결과는 동기 반환되며, 별도 Kafka 이벤트는 발행하지 않는다.
     * 숙소 점유 해제 알림은 호출자(예약 컨텍스트) 가 [com.yongchul.booking.booking.domain.event.BookingPartiallyCancelledEvent] 로 직접 발행.
     */
    override fun refundPartial(
        command: RefundTransactionUseCase.RefundPartialCommand,
    ): RefundTransactionUseCase.RefundPartialResult =
        dataService.refundPartialAndPersist(command)

    override fun findLatestPaidByBookingOrderId(
        bookingOrderId: Long,
    ): FindTransactionByBookingOrderUseCase.TransactionSummary? =
        dataService.findLatestPaidByBookingOrderId(bookingOrderId)

    fun loadTransaction(transactionId: Long): Transaction = dataService.loadTransaction(transactionId)
}
