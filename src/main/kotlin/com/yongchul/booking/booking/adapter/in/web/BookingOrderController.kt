package com.yongchul.booking.booking.adapter.`in`.web

import com.yongchul.booking.booking.adapter.`in`.web.dto.BookingOrderResponse
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
    private val checkInUseCase: CheckInUseCase,
    private val checkOutUseCase: CheckOutUseCase,
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
}
