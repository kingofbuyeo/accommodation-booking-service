package com.yongchul.booking.booking.domain

import com.yongchul.booking.booking.domain.vo.AccommodationSnapshot
import com.yongchul.booking.booking.domain.vo.RoomSnapshot
import com.yongchul.booking.common.DateRange
import com.yongchul.booking.common.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

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
    val dateRange: DateRange,
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
}
