package com.yongchul.booking.booking.domain.vo

import com.yongchul.booking.common.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded

@Embeddable
data class RoomSnapshot(
    @Column(name = "room_id") val roomId: Long,
    @Column(name = "room_name") val roomName: String,
    @Column(name = "capacity") val capacity: Int,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "snapshot_price_per_night")),
        AttributeOverride(name = "currency", column = Column(name = "snapshot_price_currency")),
    )
    val pricePerNightAtBooking: Money,
)
