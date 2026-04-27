package com.yongchul.booking.booking.application.service

import com.yongchul.booking.booking.application.port.`in`.CancelOrderUseCase
import com.yongchul.booking.booking.application.port.`in`.CheckInUseCase
import com.yongchul.booking.booking.application.port.`in`.CheckOutUseCase
import com.yongchul.booking.booking.application.port.`in`.ConfirmOrderUseCase
import com.yongchul.booking.booking.application.port.`in`.HostCancelOrderUseCase
import com.yongchul.booking.booking.application.port.`in`.PlaceOrderUseCase
import com.yongchul.booking.booking.application.port.`in`.PreviewCancellationUseCase
import com.yongchul.booking.booking.domain.BookingOrder
import com.yongchul.booking.booking.domain.BookingOrderLineItem
import com.yongchul.booking.common.event.PendingEvent
import com.yongchul.booking.common.infrastructure.kafka.DomainEventPublisher
import org.springframework.stereotype.Service

/**
 * 예약 컨텍스트 오케스트레이터 (Q3-A 일관 적용 — 3단계).
 *
 * 의도적으로 `@Transactional` 을 부착하지 않는다.
 *  → 부착 시 자식 호출(@Transactional)들이 inner-tx 로 합쳐져 commit 이 오케스트레이터 종료 시점이 되고,
 *    Kafka publish 가 commit 이전에 실행되는 dual-write 문제가 재발한다.
 *
 * 흐름:
 *   1. [BookingOrderDataService] 의 *Tx 메서드 호출 → DB 쓰기 + 이벤트 객체 반환 (이 메서드 종료 시 commit)
 *   2. 반환받은 [PendingEvent] 들을 [DomainEventPublisher] 로 Kafka 발행
 *
 * 잔여 위험: commit 직후 publish 직전에 프로세스 다운 시 이벤트 유실.
 *           Outbox 패턴 미사용 결정 (Q3-A) 으로 운영 모니터링/재처리에 의존.
 */
@Service
class BookingOrderService(
    private val dataService: BookingOrderDataService,
    private val eventPublisher: DomainEventPublisher,
) : PlaceOrderUseCase,
    ConfirmOrderUseCase,
    CancelOrderUseCase,
    CheckInUseCase,
    CheckOutUseCase,
    PreviewCancellationUseCase,
    HostCancelOrderUseCase {

    fun loadOrder(orderId: Long): BookingOrder = dataService.loadOrder(orderId)

    fun loadLineItems(orderId: Long): List<BookingOrderLineItem> = dataService.loadLineItems(orderId)

    override fun placeOrder(command: PlaceOrderUseCase.PlaceOrderCommand): BookingOrder {
        val result = dataService.placeOrderTx(command)
        publishAll(result.events)
        return result.order
    }

    override fun confirmOrder(orderId: Long) {
        publishAll(dataService.confirmOrderTx(orderId))
    }

    override fun cancelOrder(orderId: Long) {
        publishAll(dataService.cancelOrderTx(orderId))
    }

    override fun hostCancel(command: HostCancelOrderUseCase.HostCancelCommand) {
        publishAll(dataService.hostCancelTx(command))
    }

    override fun checkIn(orderId: Long) {
        publishAll(dataService.checkInTx(orderId))
    }

    override fun checkOut(orderId: Long) {
        publishAll(dataService.checkOutTx(orderId))
    }

    override fun previewCancellation(orderId: Long): PreviewCancellationUseCase.CancellationPreview =
        dataService.previewCancellation(orderId)

    private fun publishAll(events: List<PendingEvent>) {
        events.forEach { eventPublisher.publish(it.topic, it.payload) }
    }
}
