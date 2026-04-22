package com.yongchul.booking.booking.domain

import com.yongchul.booking.booking.domain.vo.GuestInfo
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 예약 주문 Aggregate Root
 *
 * 불변조건:
 * - 취소된 예약은 재확정할 수 없다.
 * - 체크인 이후 취소할 수 없다.
 * - 체크인 없이 체크아웃할 수 없다.
 * - 만료된 예약은 확정할 수 없다.
 */
@Entity
@Table(name = "booking_order")
class BookingOrder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Embedded
    val guestInfo: GuestInfo,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BookingStatus = BookingStatus.REQUESTED,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    // REQUESTED 상태일 때만 유효한 선점 만료 시각
    @Column
    var expiresAt: LocalDateTime? = null,
) {
    fun confirm() {
        require(status == BookingStatus.REQUESTED) {
            "REQUESTED 상태에서만 확정할 수 있습니다. 현재 상태: $status"
        }
        status = BookingStatus.CONFIRMED
        updatedAt = LocalDateTime.now()
    }

    fun expire() {
        require(status == BookingStatus.REQUESTED) {
            "REQUESTED 상태에서만 만료 처리할 수 있습니다. 현재 상태: $status"
        }
        status = BookingStatus.EXPIRED
        updatedAt = LocalDateTime.now()
    }

    fun checkIn() {
        require(status == BookingStatus.CONFIRMED) {
            "CONFIRMED 상태에서만 체크인할 수 있습니다. 현재 상태: $status"
        }
        status = BookingStatus.CHECKED_IN
        updatedAt = LocalDateTime.now()
    }

    fun checkOut() {
        require(status == BookingStatus.CHECKED_IN) {
            "체크인 없이 체크아웃할 수 없습니다. 현재 상태: $status"
        }
        status = BookingStatus.CHECKED_OUT
        updatedAt = LocalDateTime.now()
    }

    fun cancel() {
        require(status != BookingStatus.CANCELLED) { "이미 취소된 예약입니다." }
        require(status != BookingStatus.CHECKED_IN && status != BookingStatus.CHECKED_OUT) {
            "체크인 이후에는 취소할 수 없습니다. 현재 상태: $status"
        }
        status = BookingStatus.CANCELLED
        updatedAt = LocalDateTime.now()
    }
}
