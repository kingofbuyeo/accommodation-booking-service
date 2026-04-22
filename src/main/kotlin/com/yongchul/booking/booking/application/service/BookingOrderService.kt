package com.yongchul.booking.booking.application.service

import com.yongchul.booking.accommodation.application.port.`in`.SchedulePreemptionUseCase
import com.yongchul.booking.accommodation.application.service.AccommodationService
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderJpaRepository
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderLineItemJpaRepository
import com.yongchul.booking.booking.application.port.`in`.*
import com.yongchul.booking.booking.domain.BookingOrder
import com.yongchul.booking.booking.domain.BookingOrderLineItem
import com.yongchul.booking.booking.domain.BookingStatus
import com.yongchul.booking.booking.domain.vo.AccommodationSnapshot
import com.yongchul.booking.booking.domain.vo.RoomSnapshot
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class BookingOrderService(
    private val bookingOrderJpaRepository: BookingOrderJpaRepository,
    private val lineItemJpaRepository: BookingOrderLineItemJpaRepository,
    private val accommodationService: AccommodationService,
    private val schedulePreemptionUseCase: SchedulePreemptionUseCase,
) : PlaceOrderUseCase, ConfirmOrderUseCase, CancelOrderUseCase, CheckInUseCase, CheckOutUseCase {

    fun loadOrder(orderId: Long): BookingOrder =
        bookingOrderJpaRepository.findById(orderId).orElseThrow {
            NoSuchElementException("예약 주문을 찾을 수 없습니다: id=$orderId")
        }

    fun loadLineItems(orderId: Long): List<BookingOrderLineItem> =
        lineItemJpaRepository.findByBookingOrderId(orderId)

    @Transactional
    override fun placeOrder(command: PlaceOrderUseCase.PlaceOrderCommand): BookingOrder {
        require(!command.dateRange.checkIn.isBefore(LocalDate.now())) {
            "체크인 날짜는 오늘 이후여야 합니다. 선택한 날짜: ${command.dateRange.checkIn}"
        }

        val accommodation = accommodationService.loadAccommodation(command.accommodationId)
        val room = accommodationService.loadRoom(command.accommodationId, command.roomId)

        require(accommodationService.isRoomAvailableFor(command.roomId, command.dateRange)) {
            "선택한 기간에 예약 불가 일정이 포함되어 있습니다."
        }

        val ttl = room.calculatePreemptionTtl(command.dateRange.checkIn)
        val order = bookingOrderJpaRepository.save(
            BookingOrder(
                guestInfo = command.guestInfo,
                expiresAt = LocalDateTime.now().plus(ttl),
            )
        )

        val preempted = schedulePreemptionUseCase.preempt(
            SchedulePreemptionUseCase.PreemptCommand(
                accommodationId = command.accommodationId,
                roomId = command.roomId,
                dateRange = command.dateRange,
                bookingOrderId = order.id,
            )
        )

        if (!preempted) {
            bookingOrderJpaRepository.deleteById(order.id)
            throw IllegalStateException("이미 선점 중인 일정입니다. 다른 날짜를 선택해 주세요.")
        }

        lineItemJpaRepository.save(
            BookingOrderLineItem(
                bookingOrderId = order.id,
                accommodationSnapshot = AccommodationSnapshot(
                    accommodationId = accommodation.id,
                    accommodationName = accommodation.name,
                    address = accommodation.address,
                    hostName = accommodation.hostName,
                ),
                roomSnapshot = RoomSnapshot(
                    roomId = room.id,
                    roomName = room.name,
                    capacity = room.capacity,
                    pricePerNightAtBooking = room.pricePerNight,
                ),
                dateRange = command.dateRange,
            )
        )

        return order
    }

    @Transactional
    override fun confirmOrder(orderId: Long) {
        val order = loadOrder(orderId)
        order.confirm()
        loadLineItems(orderId).forEach { lineItem ->
            schedulePreemptionUseCase.confirmPreemption(
                SchedulePreemptionUseCase.ConfirmPreemptionCommand(
                    accommodationId = lineItem.accommodationSnapshot.accommodationId,
                    roomId = lineItem.roomSnapshot.roomId,
                    dateRange = lineItem.dateRange,
                    bookingOrderId = order.id,
                )
            )
        }
    }

    @Transactional
    override fun cancelOrder(orderId: Long) {
        val order = loadOrder(orderId)
        val wasConfirmed = order.status == BookingStatus.CONFIRMED
        order.cancel()
        loadLineItems(orderId).forEach { lineItem ->
            if (wasConfirmed) {
                schedulePreemptionUseCase.releaseConfirmed(
                    SchedulePreemptionUseCase.ReleaseConfirmedCommand(
                        accommodationId = lineItem.accommodationSnapshot.accommodationId,
                        roomId = lineItem.roomSnapshot.roomId,
                        dateRange = lineItem.dateRange,
                        bookingOrderId = order.id,
                    )
                )
            } else {
                schedulePreemptionUseCase.release(
                    SchedulePreemptionUseCase.ReleaseCommand(
                        accommodationId = lineItem.accommodationSnapshot.accommodationId,
                        roomId = lineItem.roomSnapshot.roomId,
                        dateRange = lineItem.dateRange,
                    )
                )
            }
        }
    }

    @Transactional
    override fun checkIn(orderId: Long) {
        loadOrder(orderId).checkIn()
    }

    @Transactional
    override fun checkOut(orderId: Long) {
        loadOrder(orderId).checkOut()
    }
}
