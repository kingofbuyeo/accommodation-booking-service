package com.yongchul.booking.booking.domain

import com.yongchul.booking.accommodation.domain.vo.AccommodationOperationPolicy
import com.yongchul.booking.booking.domain.vo.GuestInfo
import com.yongchul.booking.common.DateRange
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
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

    /**
     * Week3 신규: 날짜 단위 부분취소 Aggregate Root 진입점.
     *
     * 정책 및 불변식 검증을 모두 여기서 수행한 뒤 [lineItem.partiallyCancel] 에 위임한다.
     * 활성이 0박이 되면 전체취소([cancel]) 로 자동 전환된다.
     *
     * @param lineItem 대상 라인 아이템 (호출자가 미리 로드해 전달)
     * @param range 부분취소 구간
     * @param policy 숙소 운영 정책 (요청 시점 기준 — 결정 카드 #8)
     * @param requestedAt 요청 시각 (기본 오늘)
     * @return [PartialCancelResult]
     */
    fun partiallyCancel(
        lineItem: BookingOrderLineItem,
        range: DateRange,
        policy: AccommodationOperationPolicy,
        requestedAt: LocalDate = LocalDate.now(),
    ): PartialCancelResult {
        // 절대 규칙: 체크인 후 부분취소 불가
        require(status == BookingStatus.CONFIRMED) {
            "CONFIRMED 상태에서만 부분취소가 가능합니다. 현재 상태: $status"
        }

        // 정책 검증 (D-N, 허용 여부)
        val checkIn = lineItem.effectiveActiveDateRanges
            .minOfOrNull { it.checkIn }
            ?: throw IllegalStateException("활성 구간이 없는 라인 아이템입니다.")
        val decision = policy.resolvePartialCancellationDecision(
            checkInDate = checkIn,
            requestedAt = requestedAt,
        )
        require(decision.allowed) {
            decision.reasonMessage ?: "부분취소가 허용되지 않습니다."
        }

        // 라인 아이템에 부분취소 위임 (구조적 불변식 검증 포함)
        val itemResult = lineItem.partiallyCancel(range)

        // 활성 전체를 잘라낸 경우 → 전체취소로 자동 전환
        val convertedToFullCancel = itemResult.remainingNights == 0
        if (convertedToFullCancel) {
            cancel()
        } else {
            updatedAt = LocalDateTime.now()
        }

        return PartialCancelResult(
            remainingNights = itemResult.remainingNights,
            cancelledRange = itemResult.cancelledRange,
            refundRatio = decision.refundRatio,
            convertedToFullCancel = convertedToFullCancel,
        )
    }

    data class PartialCancelResult(
        val remainingNights: Int,
        val cancelledRange: DateRange,
        val refundRatio: java.math.BigDecimal,
        val convertedToFullCancel: Boolean,
    )
}
