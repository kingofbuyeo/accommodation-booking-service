package com.yongchul.booking.booking.domain.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class AccommodationSnapshot(
    @Column(name = "accommodation_id") val accommodationId: Long,
    @Column(name = "accommodation_name") val accommodationName: String,
    @Column(name = "accommodation_address") val address: String,
    @Column(name = "host_name") val hostName: String,
)
