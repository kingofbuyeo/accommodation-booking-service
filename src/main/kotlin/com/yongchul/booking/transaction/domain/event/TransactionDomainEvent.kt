package com.yongchul.booking.transaction.domain.event

import com.yongchul.booking.common.event.DomainEvent
import java.time.LocalDateTime

sealed interface TransactionDomainEvent : DomainEvent

/**
 * 결제 실패 이벤트.
 *
 * 발행: 결제 컨텍스트
 * 수신: 예약 컨텍스트 (Booking REQUESTED 유지 — TTL 만료 전 재시도 가능)
 * 숙소 컨텍스트에는 전달하지 않음 — 선점 키는 TTL까지 유지
 */
data class TransactionFailedEvent(
    val transactionId: String,
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : TransactionDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

/**
 * 부분 환불 처리 이벤트.
 *
 * 발행: 결제 컨텍스트 (partialRefund 후 PARTIAL_CANCELLED 상태)
 * 수신: 예약 컨텍스트 → DateRange 조정
 */
data class PartialRefundProcessedEvent(
    val transactionId: String,
    val bookingOrderId: String,
    val refundAmountValue: Long,
    val currency: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : TransactionDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

/**
 * 전액 환불 완료 이벤트.
 *
 * 발행: 결제 컨텍스트 (잔여 결제 금액 = 0)
 * 수신: 예약 컨텍스트 → 취소됨, 숙소 컨텍스트 → 일정 선택가능 복원
 */
data class TransactionFullyRefundedEvent(
    val transactionId: String,
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : TransactionDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

/**
 * 결제 완료 이벤트.
 *
 * 발행: 결제 컨텍스트
 * 수신: 숙소 컨텍스트 → 선점 유효 여부 확인 후 일정 확정 또는 결제 취소 요청
 *
 * kafkaPartitionKey = bookingOrderId: SchedulePreemptionExpired 와 동일 파티션으로 순서 보장.
 * TTL 만료 이벤트가 먼저 처리되어 Booking 이 EXPIRED 상태가 된 후 이 이벤트가 처리된다.
 */
data class TransactionCompletedEvent(
    val transactionId: String,
    val bookingOrderId: String,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : TransactionDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

/**
 * 결제 취소 이벤트.
 *
 * 발행: 결제 컨텍스트 (Booking EXPIRED 확인 시)
 * 수신: 예약 컨텍스트 (상태 확인용, EXPIRED 이미 처리됨)
 */
data class TransactionCancelledEvent(
    val transactionId: String,
    val bookingOrderId: String,
    val reason: TransactionCancelReason,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : TransactionDomainEvent {
    override val kafkaPartitionKey: String get() = bookingOrderId
}

enum class TransactionCancelReason {
    /** 선점 TTL 만료 후 결제 완료 */
    PREEMPTION_EXPIRED,

    /** 고객 요청 취소 */
    CUSTOMER_REQUESTED,
}