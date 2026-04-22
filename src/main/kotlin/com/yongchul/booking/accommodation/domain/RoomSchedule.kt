package com.yongchul.booking.accommodation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "room_schedule",
    uniqueConstraints = [UniqueConstraint(columnNames = ["room_id", "blocked_date"])],
)
class RoomSchedule(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Column(name = "blocked_date", nullable = false)
    val blockedDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: RoomScheduleType,

    @Column(length = 200)
    val reason: String? = null,
)
