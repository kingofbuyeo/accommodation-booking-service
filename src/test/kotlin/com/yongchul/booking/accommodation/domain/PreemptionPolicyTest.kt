package com.yongchul.booking.accommodation.domain

import com.yongchul.booking.accommodation.domain.vo.PreemptionPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

class PreemptionPolicyTest {

    private val policy = PreemptionPolicy(
        shortLeadTimeDays = 3,
        shortLeadTimeTtlMinutes = 20,
        longLeadTimeDays = 30,
        longLeadTimeTtlMinutes = 120,
        defaultTtlMinutes = 60,
    )
    private val today = LocalDate.of(2026, 5, 1)

    @Test
    fun `체크인이 3일 이내이면 단축 TTL이 적용된다`() {
        val checkIn = today.plusDays(2)
        assertThat(policy.calculateTtl(checkIn, today)).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun `체크인이 30일 이상이면 연장 TTL이 적용된다`() {
        val checkIn = today.plusDays(30)
        assertThat(policy.calculateTtl(checkIn, today)).isEqualTo(Duration.ofMinutes(120))
    }

    @Test
    fun `체크인이 중간 리드타임이면 기본 TTL이 적용된다`() {
        val checkIn = today.plusDays(14)
        assertThat(policy.calculateTtl(checkIn, today)).isEqualTo(Duration.ofMinutes(60))
    }

    @Test
    fun `체크인이 당일이면 단축 TTL이 적용된다`() {
        assertThat(policy.calculateTtl(today, today)).isEqualTo(Duration.ofMinutes(20))
    }
}
