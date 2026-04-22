package com.yongchul.booking.accommodation.domain

import com.yongchul.booking.accommodation.domain.vo.PreemptionPolicy
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
@Table(name = "accommodation")
class Accommodation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, length = 200)
    val address: String,

    @Column(length = 500)
    val description: String? = null,

    @Column(nullable = false, length = 100)
    val hostName: String,

    // ddl-auto: update로 컬럼이 나중에 추가된 경우 기존 row는 null로 로드될 수 있음
    @Embedded
    var preemptionPolicy: PreemptionPolicy? = PreemptionPolicy(),
) {
    fun calculatePreemptionTtl(checkInDate: LocalDate): Duration =
        (preemptionPolicy ?: PreemptionPolicy()).calculateTtl(checkInDate)
}
