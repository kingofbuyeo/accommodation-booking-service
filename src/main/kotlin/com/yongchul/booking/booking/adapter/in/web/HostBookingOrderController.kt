package com.yongchul.booking.booking.adapter.`in`.web

import com.yongchul.booking.booking.application.port.`in`.HostCancelOrderUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 호스트 운영용 예약 관리 엔드포인트.
 *
 * 일반 고객 엔드포인트와 분리하여 권한/감사 추적을 명확히 한다.
 * 현재는 호스트 인증이 도입되지 않은 상태이지만, 별도 path 로 분리해 추후 보안 정책 부착이 용이하도록 한다.
 */
@RestController
@RequestMapping("/api/v1/host/booking-orders")
class HostBookingOrderController(
    private val hostCancelOrderUseCase: HostCancelOrderUseCase,
) {
    @PostMapping("/{orderId}/cancel")
    fun hostCancel(
        @PathVariable orderId: Long,
        @RequestBody request: HostCancelRequest,
    ): ResponseEntity<Unit> {
        hostCancelOrderUseCase.hostCancel(
            HostCancelOrderUseCase.HostCancelCommand(orderId = orderId, reason = request.reason)
        )
        return ResponseEntity.ok().build()
    }

    data class HostCancelRequest(
        val reason: String,
    )
}
