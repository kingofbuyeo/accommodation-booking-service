package com.yongchul.booking.accommodation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Q5 결정 — Redis 장애 시 선점 충돌 방어용 DB 백업 테이블.
 *
 * Redis SETNX 가 실패(접속 불가, 네트워크 단절 등)할 때 이 테이블에 INSERT 를 시도해
 * (room_id, reserved_date) Unique 제약으로 race 를 막는다.
 *
 * - 정상 운영 시(Redis 정상)에는 사용되지 않음
 * - Redis 장애 동안에만 active record 가 누적
 * - expiresAt 으로 만료 추적, 별도 스케줄러로 정리 가능 (현재는 단순 INSERT/DELETE 만)
 */
@Entity
@Table(
    name = "preemption_fallback",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_preemption_fallback_room_date", columnNames = ["room_id", "reserved_date"])
    ],
    indexes = [
        Index(name = "idx_preemption_fallback_booking", columnList = "booking_order_id"),
    ],
)
class PreemptionFallback(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false) val accommodationId: Long,
    @Column(name = "room_id", nullable = false) val roomId: Long,
    @Column(name = "reserved_date", nullable = false) val reservedDate: LocalDate,
    @Column(name = "booking_order_id", nullable = false) val bookingOrderId: Long,
    @Column(name = "expires_at", nullable = false) val expiresAt: LocalDateTime,
)
