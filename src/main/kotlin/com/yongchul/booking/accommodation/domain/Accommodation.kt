package com.yongchul.booking.accommodation.domain

import com.yongchul.booking.accommodation.domain.vo.AccommodationOperationPolicy
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

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
    var operationPolicy: AccommodationOperationPolicy? = AccommodationOperationPolicy(),

    /**
     * 숙소 체크인 가능 시각 (Q2a — 자동 체크인 처리 기준).
     * 이 시각이 지나면 시스템이 자동으로 체크인 처리하여 "체크인 후 취소 불가" 불변조건을 보장한다.
     * null 인 경우 [DEFAULT_CHECK_IN_TIME] 적용.
     */
    @Column(name = "check_in_time")
    var checkInTime: LocalTime? = DEFAULT_CHECK_IN_TIME,
) {
    fun calculatePreemptionTtl(checkInDate: LocalDate): Duration =
        resolvedPolicy().calculateTtl(checkInDate)

    fun resolvedPolicy(): AccommodationOperationPolicy = operationPolicy ?: AccommodationOperationPolicy()

    fun effectiveCheckInTime(): LocalTime = checkInTime ?: DEFAULT_CHECK_IN_TIME

    companion object {
        val DEFAULT_CHECK_IN_TIME: LocalTime = LocalTime.of(15, 0)
    }
}
