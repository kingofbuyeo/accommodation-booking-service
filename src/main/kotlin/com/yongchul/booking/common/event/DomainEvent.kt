package com.yongchul.booking.common.event

import java.time.LocalDateTime

/**
 * 모든 도메인 이벤트의 기반 인터페이스.
 * kafkaPartitionKey: 동일 예약에 관련된 이벤트는 같은 파티션에서 순서 보장
 */
interface DomainEvent {
    val occurredAt: LocalDateTime
    val kafkaPartitionKey: String
}