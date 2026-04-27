package com.yongchul.booking.common.event

/**
 * 트랜잭션 커밋 이후 발행할 도메인 이벤트의 staging 표현.
 *
 * Q3-A 의 일관 적용을 위해 도입됨.
 *
 * 흐름:
 *   1. `*DataService.X_Tx(...)` 메서드 (`@Transactional`) 가 DB 변경을 수행하고
 *      발행해야 할 이벤트를 [PendingEvent] 목록으로 **반환만** 한다 (publish 하지 않음).
 *   2. 호출자(plain 오케스트레이터)는 DataService 메서드가 정상 반환되면
 *      (= 트랜잭션 commit 완료) 반환받은 [PendingEvent] 들을 Kafka 로 발행한다.
 *
 * 장점: outer @Transactional 와 inner publish 의 dual-write 문제 회피, REQUIRES_NEW 의
 *       부분 commit 정합성 문제 회피.
 *
 * 잔여 위험: commit 직후 publish 직전에 프로세스 다운 시 이벤트 유실. Outbox 패턴 대신
 *           수용 (Q3-A 결정).
 */
data class PendingEvent(
    val topic: String,
    val payload: DomainEvent,
)
