package com.yongchul.booking.booking.adapter.`in`.web

import com.yongchul.booking.booking.adapter.`in`.web.dto.BookingOrderResponse
import com.yongchul.booking.booking.adapter.`in`.web.dto.CancellationPreviewResponse
import com.yongchul.booking.booking.adapter.`in`.web.dto.PartialCancelRequest
import com.yongchul.booking.booking.adapter.`in`.web.dto.PartialCancelResponse
import com.yongchul.booking.booking.adapter.`in`.web.dto.PartialCancellationPreviewResponse
import com.yongchul.booking.booking.adapter.`in`.web.dto.PlaceOrderRequest
import com.yongchul.booking.booking.application.port.`in`.*
import com.yongchul.booking.booking.application.service.BookingOrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/booking-orders")
class BookingOrderController(
    private val placeOrderUseCase: PlaceOrderUseCase,
    private val confirmOrderUseCase: ConfirmOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val previewCancellationUseCase: PreviewCancellationUseCase,
    private val checkInUseCase: CheckInUseCase,
    private val checkOutUseCase: CheckOutUseCase,
    private val partialCancelOrderUseCase: PartialCancelOrderUseCase,
    private val previewPartialCancellationUseCase: PreviewPartialCancellationUseCase,
    private val bookingOrderService: BookingOrderService,
) {
    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: Long): ResponseEntity<BookingOrderResponse> {
        val order = bookingOrderService.loadOrder(orderId)
        val lineItems = bookingOrderService.loadLineItems(orderId)
        return ResponseEntity.ok(BookingOrderResponse.from(order, lineItems))
    }

    @PostMapping
    fun placeOrder(@RequestBody request: PlaceOrderRequest): ResponseEntity<BookingOrderResponse> {
        val order = placeOrderUseCase.placeOrder(request.toCommand())
        val lineItems = bookingOrderService.loadLineItems(order.id)
        return ResponseEntity.ok(BookingOrderResponse.from(order, lineItems))
    }

    @PostMapping("/{orderId}/confirm")
    fun confirmOrder(@PathVariable orderId: Long): ResponseEntity<Unit> {
        confirmOrderUseCase.confirmOrder(orderId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(@PathVariable orderId: Long): ResponseEntity<Unit> {
        cancelOrderUseCase.cancelOrder(orderId)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{orderId}/cancellation-preview")
    fun previewCancellation(@PathVariable orderId: Long): ResponseEntity<CancellationPreviewResponse> {
        val preview = previewCancellationUseCase.previewCancellation(orderId)
        return ResponseEntity.ok(CancellationPreviewResponse.from(preview))
    }

    @PostMapping("/{orderId}/check-in")
    fun checkIn(@PathVariable orderId: Long): ResponseEntity<Unit> {
        checkInUseCase.checkIn(orderId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{orderId}/check-out")
    fun checkOut(@PathVariable orderId: Long): ResponseEntity<Unit> {
        checkOutUseCase.checkOut(orderId)
        return ResponseEntity.ok().build()
    }

    // ─── Week3 신규: 부분취소 ───

    /**
     * 부분취소 미리보기 — DB 변경 없음.
     * 호스트 정책 불가/기간 초과 시에도 200 OK 로 `partialCancelable=false` 와 사유를 반환한다.
     */
    @GetMapping("/{orderId}/line-items/{lineItemId}/partial-cancellation-preview")
    fun previewPartialCancellation(
        @PathVariable orderId: Long,
        @PathVariable lineItemId: Long,
        @RequestParam cancelCheckIn: java.time.LocalDate,
        @RequestParam cancelCheckOut: java.time.LocalDate,
    ): ResponseEntity<PartialCancellationPreviewResponse> {
        val preview = previewPartialCancellationUseCase.previewPartialCancellation(
            PreviewPartialCancellationUseCase.PreviewPartialCancelCommand(
                orderId = orderId,
                lineItemId = lineItemId,
                cancelCheckIn = cancelCheckIn,
                cancelCheckOut = cancelCheckOut,
            )
        )
        return ResponseEntity.ok(PartialCancellationPreviewResponse.from(preview))
    }

    /**
     * 부분취소 확정.
     * 정책 위반 시 400, 도메인 불변식 위반 시 400 을 반환한다.
     */
    @PostMapping("/{orderId}/line-items/{lineItemId}/partial-cancel")
    fun partialCancelOrder(
        @PathVariable orderId: Long,
        @PathVariable lineItemId: Long,
        @RequestBody request: PartialCancelRequest,
    ): ResponseEntity<PartialCancelResponse> {
        val result = partialCancelOrderUseCase.partialCancelOrder(
            PartialCancelOrderUseCase.PartialCancelCommand(
                orderId = orderId,
                lineItemId = lineItemId,
                cancelCheckIn = request.cancelCheckIn,
                cancelCheckOut = request.cancelCheckOut,
            )
        )
        return ResponseEntity.ok(
            PartialCancelResponse(
                orderId = result.orderId,
                refundAmount = result.refundAmount.amount,
                penaltyAmount = result.penaltyAmount.amount,
                currency = result.refundAmount.currency,
                remainingNights = result.remainingNights,
                convertedToFullCancel = result.convertedToFullCancel,
            )
        )
    }
}
