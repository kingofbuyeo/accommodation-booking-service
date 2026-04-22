package com.yongchul.booking.booking.domain.vo

import jakarta.persistence.Embeddable

@Embeddable
data class GuestInfo(
    val guestName: String,
    val phone: String,
    val headcount: Int,
) {
    init {
        require(guestName.isNotBlank()) { "예약자 이름은 필수입니다." }
        require(headcount > 0) { "인원 수는 1명 이상이어야 합니다." }
    }
}
