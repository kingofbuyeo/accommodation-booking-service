package com.yongchul.booking.booking.application.port.`in`

/**
 * 호스트 발 강제 취소 유스케이스.
 *
 * Q7/Q9 결정: 호스트가 시설 문제 등으로 예약을 강제 취소하는 경로는 고객 취소와 별도이며,
 * **페널티 미부과 + 100% 환불** 정책을 적용한다. 손님이 압박을 받아 자발적으로 취소하는
 * 어뷰즈와 시스템적으로 분리하기 위해 별도 엔드포인트/유스케이스로 노출한다.
 */
interface HostCancelOrderUseCase {
    fun hostCancel(command: HostCancelCommand)

    data class HostCancelCommand(
        val orderId: Long,
        val reason: String,
    )
}
