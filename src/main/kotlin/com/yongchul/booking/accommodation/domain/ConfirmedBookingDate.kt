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

@Entity
@Table(
    name = "confirmed_booking_date",
    uniqueConstraints = [UniqueConstraint(columnNames = ["room_id", "reserved_date"])],
    indexes = [Index(name = "idx_confirmed_booking_date_room_date", columnList = "room_id, reserved_date")],
)
class ConfirmedBookingDate(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false) val accommodationId: Long,
    @Column(nullable = false) val roomId: Long,
    @Column(nullable = false) val reservedDate: LocalDate,
    @Column(nullable = false) val bookingOrderId: Long,
)
