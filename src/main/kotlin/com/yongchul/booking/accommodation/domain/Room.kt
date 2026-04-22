package com.yongchul.booking.accommodation.domain

import com.yongchul.booking.accommodation.domain.vo.PreemptionPolicy
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
import jakarta.persistence.Table
import java.time.Duration
import java.time.LocalDate

@Entity
@Table(name = "room")
class Room(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "accommodation_id", nullable = false)
    val accommodationId: Long,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false)
    val capacity: Int,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "price_per_night", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "price_currency", nullable = false)),
    )
    val pricePerNight: Money,

    // ddl-auto: update로 컬럼이 나중에 추가된 경우 기존 row는 null로 로드될 수 있음
    @Embedded
    var preemptionPolicy: PreemptionPolicy? = PreemptionPolicy(),
) {
    fun isAvailableFor(schedules: List<RoomSchedule>, dateRange: DateRange): Boolean =
        schedules.none { dateRange.contains(it.blockedDate) }

    fun calculateTotalPrice(dateRange: DateRange): Money = pricePerNight * dateRange.nights

    fun calculatePreemptionTtl(checkInDate: LocalDate): Duration =
        (preemptionPolicy ?: PreemptionPolicy()).calculateTtl(checkInDate)
}
