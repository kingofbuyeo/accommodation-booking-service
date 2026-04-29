package com.yongchul.booking.booking.domain

import com.yongchul.booking.accommodation.domain.vo.AccommodationOperationPolicy
import com.yongchul.booking.booking.adapter.out.persistence.DailyPriceListConverter
import com.yongchul.booking.booking.adapter.out.persistence.DateRangeListConverter
import com.yongchul.booking.booking.domain.vo.AccommodationSnapshot
import com.yongchul.booking.booking.domain.vo.DailyPrice
import com.yongchul.booking.booking.domain.vo.RoomSnapshot
import com.yongchul.booking.common.DateRange
import com.yongchul.booking.common.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 예약 주문 라인 아이템.
 *
 * Week3에서 날짜 단위 부분취소를 지원하기 위해 [activeDateRanges], [cancelledDateRanges],
 * [dailyPriceSnapshots] 필드가 추가됐다.
 *
 * 불변식:
 * - [activeDateRanges] 과 [cancelledDateRanges] 의 날짜 합집합 == 원본 예약 날짜 집합
 * - 두 리스트의 날짜 교집합 == ∅
 * - [dateRange] == 활성 구간 전체의 [checkIn, checkOut)
 */
@Entity
@Table(
    name = "booking_order_line_item",
    indexes = [
        Index(name = "idx_line_item_room_checkin_checkout", columnList = "room_id, check_in, check_out"),
    ],
)
class BookingOrderLineItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "booking_order_id", nullable = false)
    val bookingOrderId: Long,

    @Embedded
    val accommodationSnapshot: AccommodationSnapshot,

    @Embedded
    val roomSnapshot: RoomSnapshot,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "checkIn", column = Column(name = "check_in", nullable = false)),
        AttributeOverride(name = "checkOut", column = Column(name = "check_out", nullable = false)),
    )
    var dateRange: DateRange,

    /**
     * 현재 활성(미취소) 날짜 구간 목록. 부분취소 후 갱신된다.
     * 기존 예약은 최초 [dateRange] 와 동일한 단일 구간을 갖는다.
     * NULL → 마이그레이션 호환: 빈 리스트가 아닌 경우 [dateRange] 기반으로 보정한다.
     */
    @Convert(converter = DateRangeListConverter::class)
    @Column(name = "active_date_ranges", columnDefinition = "TEXT")
    var activeDateRanges: List<DateRange>? = null,

    /**
     * 부분취소로 취소된 날짜 구간 목록 (append-only).
     */
    @Convert(converter = DateRangeListConverter::class)
    @Column(name = "cancelled_date_ranges", columnDefinition = "TEXT")
    var cancelledDateRanges: List<DateRange> = emptyList(),

    /**
     * 예약 시점 1박 가격 스냅샷. 부분취소 환불 금액 계산의 기준이 된다.
     * NULL → 마이그레이션 호환: [roomSnapshot.pricePerNightAtBooking] 으로 fallback.
     */
    @Convert(converter = DailyPriceListConverter::class)
    @Column(name = "daily_price_snapshots", columnDefinition = "TEXT")
    var dailyPriceSnapshots: List<DailyPrice>? = null,
) {
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "line_total_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "line_total_currency", nullable = false)),
    )
    val lineTotal: Money = roomSnapshot.pricePerNightAtBooking * dateRange.nights

    val nights: Int get() = dateRange.nights
    val checkIn: LocalDate get() = dateRange.checkIn
    val checkOut: LocalDate get() = dateRange.checkOut

    /** 활성 구간의 유효 날짜 수 */
    val activeNights: Int
        get() = effectiveActiveDateRanges.sumOf { it.nights }

    /** NULL-safe 활성 구간 목록 (마이그레이션 호환) */
    val effectiveActiveDateRanges: List<DateRange>
        get() = activeDateRanges?.takeIf { it.isNotEmpty() } ?: listOf(dateRange)

    /**
     * 부분취소 도메인 메서드.
     *
     * [range] 구간을 활성 목록에서 잘라내 취소 목록으로 이동하고 [dateRange] 를 갱신한다.
     * 활성이 0박이 되면 true 를 반환해 호출자([BookingOrder]) 가 전체취소 전이를 수행하도록 한다.
     *
     * 정책 검증(D-N, 체크인 후 불가 등)은 호출 전에 [BookingOrder.partiallyCancel] 에서 수행하며,
     * 이 메서드는 구조적 불변식(연속, 위치)만 검사한다.
     *
     * @return 부분취소 후 활성 박수. 0 이면 전체취소로 변환이 필요하다.
     */
    fun partiallyCancel(range: DateRange): PartialCancelResult {
        val active = effectiveActiveDateRanges.toMutableList()

        // 활성 전체 == 요청 범위: 취소 이력은 기록하되 활성을 비운다
        if (active.size == 1 && active[0] == range) {
            cancelledDateRanges = cancelledDateRanges + range
            activeDateRanges = emptyList()
            return PartialCancelResult(remainingNights = 0, cancelledRange = range)
        }

        // 활성 구간에서 해당 range 가 포함된 구간을 찾아 잘라냄
        val targetIdx = active.indexOfFirst { it.containsRange(range) }
        require(targetIdx >= 0) {
            "활성 구간에 요청 범위가 포함되지 않습니다. 활성=$active, 요청=$range"
        }

        val target = active[targetIdx]
        val remaining = target.subtract(range)

        if (remaining == null) {
            // 해당 활성 구간을 통째로 잘라냄
            active.removeAt(targetIdx)
        } else {
            active[targetIdx] = remaining
        }

        // 불변식: 첫날 또는 마지막날 포함 연속 구간 — subtract 내부에서 이미 검증됨

        val newCancelledList = cancelledDateRanges + range
        cancelledDateRanges = newCancelledList
        activeDateRanges = active

        // DateRange 갱신: 활성이 남아 있으면 [first.checkIn, last.checkOut)
        if (active.isEmpty()) {
            return PartialCancelResult(remainingNights = 0, cancelledRange = range)
        }

        val sorted = active.sortedBy { it.checkIn }
        val newCheckIn = sorted.first().checkIn
        val newCheckOut = sorted.last().checkOut
        dateRange = DateRange(checkIn = newCheckIn, checkOut = newCheckOut)

        return PartialCancelResult(
            remainingNights = active.sumOf { it.nights },
            cancelledRange = range,
        )
    }

    /**
     * 취소 구간의 날짜별 가격 합산.
     * [dailyPriceSnapshots] 가 없으면 [roomSnapshot.pricePerNightAtBooking] 으로 fallback.
     */
    fun computeCancelledAmount(range: DateRange): Money {
        val snapshots = dailyPriceSnapshots
        if (snapshots.isNullOrEmpty()) {
            return roomSnapshot.pricePerNightAtBooking * range.nights
        }
        val cancelDates = range.dates().toSet()
        val total = snapshots
            .filter { it.date in cancelDates }
            .fold(Money.ZERO) { acc, dp -> acc + dp.amount }

        // 스냅샷에 해당 날짜가 없는 경우(레거시 데이터) fallback
        return if (total.amount.signum() > 0) total
        else roomSnapshot.pricePerNightAtBooking * range.nights
    }

    /**
     * 부분취소 환불 금액 계산.
     * [refundRatio] 는 [AccommodationOperationPolicy.resolvePartialCancellationDecision] 에서 가져온다.
     */
    fun computePartialRefundAmount(range: DateRange, refundRatio: java.math.BigDecimal): Money {
        val cancelledAmount = computeCancelledAmount(range)
        val refund = cancelledAmount.amount.multiply(refundRatio).setScale(0, RoundingMode.DOWN)
        return Money(amount = refund, currency = cancelledAmount.currency)
    }

    data class PartialCancelResult(
        val remainingNights: Int,
        val cancelledRange: DateRange,
    )
}
