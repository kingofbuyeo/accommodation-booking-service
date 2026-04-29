package com.yongchul.booking.booking.application.service

import com.yongchul.booking.accommodation.application.port.`in`.CalculateCancellationRefundUseCase
import com.yongchul.booking.accommodation.application.port.`in`.SchedulePreemptionUseCase
import com.yongchul.booking.accommodation.application.service.AccommodationService
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderJpaRepository
import com.yongchul.booking.booking.adapter.out.persistence.BookingOrderLineItemJpaRepository
import com.yongchul.booking.booking.application.port.`in`.HostCancelOrderUseCase
import com.yongchul.booking.booking.application.port.`in`.PartialCancelOrderUseCase
import com.yongchul.booking.booking.application.port.`in`.PartialCancellationPreview
import com.yongchul.booking.booking.application.port.`in`.PlaceOrderUseCase
import com.yongchul.booking.booking.application.port.`in`.PreviewCancellationUseCase
import com.yongchul.booking.booking.application.port.`in`.PreviewPartialCancellationUseCase
import com.yongchul.booking.booking.domain.BookingOrder
import com.yongchul.booking.booking.domain.BookingOrderLineItem
import com.yongchul.booking.booking.domain.BookingStatus
import com.yongchul.booking.booking.domain.event.BookingCancelledEvent
import com.yongchul.booking.booking.domain.event.BookingPartiallyCancelledEvent
import com.yongchul.booking.booking.domain.vo.AccommodationSnapshot
import com.yongchul.booking.booking.domain.vo.DailyPrice
import com.yongchul.booking.booking.domain.vo.RoomSnapshot
import com.yongchul.booking.common.DateRange
import com.yongchul.booking.common.Money
import com.yongchul.booking.common.event.PendingEvent
import com.yongchul.booking.common.infrastructure.kafka.KafkaTopics
import com.yongchul.booking.transaction.application.port.`in`.InitiateTransactionUseCase
import com.yongchul.booking.transaction.application.port.`in`.RefundTransactionUseCase
import com.yongchul.booking.transaction.application.service.TransactionDataService
import com.yongchul.booking.transaction.domain.vo.LedgerInfo
import com.yongchul.booking.transaction.domain.vo.RefundAmount
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 예약 컨텍스트 DB 작업 전담 서비스.
 *
 * Q3-A 일관 적용 (3단계 — 컨슈머·스케줄러까지):
 *   - 모든 *Tx 메서드는 `@Transactional` 안에서 DB 변경을 완료하고 발행할 이벤트를 [PendingEvent] 목록으로 반환
 *   - 호출자([BookingOrderService] 오케스트레이터, 컨슈머, 스케줄러) 는 메서드 정상 반환 후 (= commit 완료)
 *     반환받은 이벤트를 Kafka 로 발행
 *
 * 트랜잭션 컨텍스트 호출은 [TransactionDataService] 직접 사용 (TransactionService 의 inner publish 회피).
 */
@Service
@Transactional(readOnly = true)
class BookingOrderDataService(
    private val bookingOrderJpaRepository: BookingOrderJpaRepository,
    private val lineItemJpaRepository: BookingOrderLineItemJpaRepository,
    private val accommodationService: AccommodationService,
    private val schedulePreemptionUseCase: SchedulePreemptionUseCase,
    private val calculateCancellationRefundUseCase: CalculateCancellationRefundUseCase,
    private val transactionDataService: TransactionDataService,
) {
    /** 읽기 전용 — controller GET, preview 등에서 사용. 락 없음. */
    fun loadOrder(orderId: Long): BookingOrder =
        bookingOrderJpaRepository.findById(orderId).orElseThrow {
            NoSuchElementException("예약 주문을 찾을 수 없습니다: id=$orderId")
        }

    /** 쓰기 경로 전용 — 반드시 `@Transactional` 안에서만 호출. */
    fun loadOrderForUpdate(orderId: Long): BookingOrder =
        bookingOrderJpaRepository.findByIdForUpdate(orderId).orElseThrow {
            NoSuchElementException("예약 주문을 찾을 수 없습니다: id=$orderId")
        }

    fun loadLineItems(orderId: Long): List<BookingOrderLineItem> =
        lineItemJpaRepository.findByBookingOrderId(orderId)

    // ───────────────────── REST 진입 경로 ─────────────────────

    @Transactional
    fun placeOrderTx(command: PlaceOrderUseCase.PlaceOrderCommand): PlaceOrderResult {
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
            // tx rollback 으로 booking 도 자동 제거되지만 명시적 delete 는 의도 표현
            bookingOrderJpaRepository.deleteById(order.id)
            throw IllegalStateException("이미 선점 중인 일정입니다. 다른 날짜를 선택해 주세요.")
        }

        // 예약 시점 날짜별 가격 스냅샷 생성 (부분취소 환불 계산 기준)
        val dailyPriceSnapshots = command.dateRange.dates().map { date ->
            DailyPrice(date = date, amount = room.pricePerNight)
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
                activeDateRanges = listOf(command.dateRange),
                cancelledDateRanges = emptyList(),
                dailyPriceSnapshots = dailyPriceSnapshots,
            )
        )

        return PlaceOrderResult(order = order, events = emptyList())
    }

    @Transactional
    fun confirmOrderTx(orderId: Long): List<PendingEvent> {
        val order = loadOrderForUpdate(orderId)
        if (order.status != BookingStatus.REQUESTED) {
            // 멱등 처리 — 이미 처리됐거나 만료
            return emptyList()
        }
        val lineItems = loadLineItems(orderId)
        require(lineItems.isNotEmpty()) { "라인 아이템이 없는 예약은 확정할 수 없습니다." }

        val events = mutableListOf<PendingEvent>()
        val totalAmount = lineItems.fold(Money.ZERO) { acc, item -> acc + item.lineTotal }

        // Transaction 생성/완료 — DataService 직접 호출로 inner publish 회피
        val transaction = transactionDataService.initiate(
            InitiateTransactionUseCase.InitiateCommand(bookingOrderId = orderId)
        )
        val txCompletedEvent = transactionDataService.completeAndPersist(
            com.yongchul.booking.transaction.application.port.`in`.ProcessTransactionUseCase.CompleteCommand(
                transactionId = transaction.id,
                ledgerInfo = LedgerInfo(
                    pgTransactionId = "MOCK-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                    approvalNumber = "APPR-${System.currentTimeMillis()}",
                    pgName = "MockPG",
                    paidAmount = totalAmount,
                ),
            )
        )
        events.add(PendingEvent(KafkaTopics.TRANSACTION_EVENTS, txCompletedEvent))

        order.confirm()
        lineItems.forEach { lineItem ->
            schedulePreemptionUseCase.confirmPreemption(
                SchedulePreemptionUseCase.ConfirmPreemptionCommand(
                    accommodationId = lineItem.accommodationSnapshot.accommodationId,
                    roomId = lineItem.roomSnapshot.roomId,
                    dateRange = lineItem.dateRange,
                    bookingOrderId = order.id,
                )
            )
        }
        return events
    }

    @Transactional
    fun cancelOrderTx(orderId: Long): List<PendingEvent> {
        val order = loadOrderForUpdate(orderId)
        val lineItems = loadLineItems(orderId)

        return when (order.status) {
            BookingStatus.REQUESTED -> {
                cancelUnpaidOrder(order, lineItems)
                emptyList()
            }
            BookingStatus.CONFIRMED -> cancelConfirmedOrderInternal(
                order = order,
                lineItems = lineItems,
                hostForceFullRefund = false,
                reasonText = "고객 요청 취소 — 숙소 페널티 정책 적용",
            )
            else -> throw IllegalStateException("취소할 수 없는 예약 상태입니다: ${order.status}")
        }
    }

    @Transactional
    fun hostCancelTx(command: HostCancelOrderUseCase.HostCancelCommand): List<PendingEvent> {
        val order = loadOrderForUpdate(command.orderId)
        require(order.status == BookingStatus.REQUESTED || order.status == BookingStatus.CONFIRMED) {
            "REQUESTED 또는 CONFIRMED 상태에서만 호스트 강제 취소가 가능합니다. 현재 상태: ${order.status}"
        }
        val lineItems = loadLineItems(command.orderId)
        if (order.status == BookingStatus.REQUESTED) {
            cancelUnpaidOrder(order, lineItems)
            return emptyList()
        }
        return cancelConfirmedOrderInternal(
            order = order,
            lineItems = lineItems,
            hostForceFullRefund = true,
            reasonText = "호스트 강제 취소 — 100% 환불 (사유: ${command.reason})",
        )
    }

    @Transactional
    fun checkInTx(orderId: Long): List<PendingEvent> {
        loadOrderForUpdate(orderId).checkIn()
        return emptyList()
    }

    @Transactional
    fun checkOutTx(orderId: Long): List<PendingEvent> {
        loadOrderForUpdate(orderId).checkOut()
        return emptyList()
    }

    // ───────────────────── 컨슈머 진입 경로 ─────────────────────

    @Transactional
    fun expireFromEventTx(bookingOrderId: Long): List<PendingEvent> {
        val booking = bookingOrderJpaRepository.findByIdForUpdate(bookingOrderId).orElse(null)
            ?: return emptyList()
        if (booking.status != BookingStatus.REQUESTED) return emptyList()
        booking.expire()
        return emptyList()
    }

    @Transactional
    fun confirmFromEventTx(bookingOrderId: Long): List<PendingEvent> {
        val booking = bookingOrderJpaRepository.findByIdForUpdate(bookingOrderId).orElse(null)
            ?: return emptyList()
        if (booking.status != BookingStatus.REQUESTED) return emptyList()
        booking.confirm()
        return emptyList()
    }

    @Transactional
    fun cancelFromTransactionEventTx(bookingOrderId: String): List<PendingEvent> {
        val events = mutableListOf<PendingEvent>()
        val booking = bookingOrderJpaRepository.findByIdForUpdate(bookingOrderId.toLong()).orElse(null)
            ?: return events
        if (booking.status == BookingStatus.CANCELLED) return events
        val lineItems = lineItemJpaRepository.findByBookingOrderId(booking.id)
        booking.cancel()
        lineItems.forEach { lineItem ->
            schedulePreemptionUseCase.releaseConfirmed(
                SchedulePreemptionUseCase.ReleaseConfirmedCommand(
                    accommodationId = lineItem.accommodationSnapshot.accommodationId,
                    roomId = lineItem.roomSnapshot.roomId,
                    dateRange = lineItem.dateRange,
                    bookingOrderId = booking.id,
                )
            )
        }
        events.add(PendingEvent(KafkaTopics.BOOKING_EVENTS, BookingCancelledEvent(bookingOrderId = bookingOrderId)))
        return events
    }

    // ───────────────────── 부분취소 (Week3 신규) ─────────────────────

    /**
     * 부분취소 미리보기 (DB 변경 없음).
     */
    fun previewPartialCancellation(
        command: PreviewPartialCancellationUseCase.PreviewPartialCancelCommand,
    ): PartialCancellationPreview {
        val order = loadOrder(command.orderId)
        require(order.status == BookingStatus.CONFIRMED) {
            "CONFIRMED 상태의 예약만 부분취소 미리보기가 가능합니다. 현재 상태: ${order.status}"
        }

        val lineItem = lineItemJpaRepository.findById(command.lineItemId).orElseThrow {
            NoSuchElementException("라인 아이템을 찾을 수 없습니다: id=${command.lineItemId}")
        }
        require(lineItem.bookingOrderId == command.orderId) {
            "라인 아이템이 해당 예약에 속하지 않습니다."
        }

        val cancelRange = DateRange(checkIn = command.cancelCheckIn, checkOut = command.cancelCheckOut)
        val room = accommodationService.loadRoom(
            lineItem.accommodationSnapshot.accommodationId,
            lineItem.roomSnapshot.roomId,
        )
        val policy = room.resolvedPolicy()
        val checkIn = lineItem.effectiveActiveDateRanges.minOf { it.checkIn }
        val decision = policy.resolvePartialCancellationDecision(checkInDate = checkIn)

        if (!decision.allowed) {
            return PartialCancellationPreview(
                orderId = command.orderId,
                lineItemId = command.lineItemId,
                cancelCheckIn = command.cancelCheckIn,
                cancelCheckOut = command.cancelCheckOut,
                cancelNights = 0,
                cancelledAmount = Money.ZERO,
                refundAmount = Money.ZERO,
                penaltyAmount = Money.ZERO,
                refundRatio = BigDecimal.ZERO,
                partialCancelable = false,
                reasonCode = decision.reasonCode,
                reasonMessage = decision.reasonMessage,
            )
        }

        val cancelledAmount = lineItem.computeCancelledAmount(cancelRange)
        val refundAmount = lineItem.computePartialRefundAmount(cancelRange, decision.refundRatio)
        val penaltyAmount = Money(
            amount = cancelledAmount.amount - refundAmount.amount,
            currency = cancelledAmount.currency,
        )

        return PartialCancellationPreview(
            orderId = command.orderId,
            lineItemId = command.lineItemId,
            cancelCheckIn = command.cancelCheckIn,
            cancelCheckOut = command.cancelCheckOut,
            cancelNights = cancelRange.nights,
            cancelledAmount = cancelledAmount,
            refundAmount = refundAmount,
            penaltyAmount = penaltyAmount,
            refundRatio = decision.refundRatio,
            partialCancelable = true,
            reasonCode = null,
            reasonMessage = null,
        )
    }

    /**
     * 부분취소 실행 트랜잭션.
     *
     * 흐름: 도메인 검증 → 결제 부분환불(동기) → lineItem 갱신 + commit → BookingPartiallyCancelledEvent 반환
     * 활성 전체 == 요청 범위인 경우 전체취소(cancelOrderTx) 흐름으로 자동 변환.
     */
    @Transactional
    fun partialCancelTx(command: PartialCancelOrderUseCase.PartialCancelCommand): PartialCancelTxResult {
        val order = loadOrderForUpdate(command.orderId)
        require(order.status == BookingStatus.CONFIRMED) {
            "CONFIRMED 상태에서만 부분취소가 가능합니다. 현재 상태: ${order.status}"
        }

        val lineItem = lineItemJpaRepository.findById(command.lineItemId).orElseThrow {
            NoSuchElementException("라인 아이템을 찾을 수 없습니다: id=${command.lineItemId}")
        }
        require(lineItem.bookingOrderId == command.orderId) {
            "라인 아이템이 해당 예약에 속하지 않습니다."
        }

        val cancelRange = DateRange(checkIn = command.cancelCheckIn, checkOut = command.cancelCheckOut)
        val room = accommodationService.loadRoom(
            lineItem.accommodationSnapshot.accommodationId,
            lineItem.roomSnapshot.roomId,
        )
        val policy = room.resolvedPolicy()

        // 도메인 메서드 (정책 + 불변식 검증 포함)
        val domainResult = order.partiallyCancel(
            lineItem = lineItem,
            range = cancelRange,
            policy = policy,
        )

        // 전체취소로 변환된 경우 — 기존 cancelOrderTx 흐름과 동일하게 결제 전액 환불
        if (domainResult.convertedToFullCancel) {
            val fullCancelEvents = cancelConfirmedOrderInternal(
                order = order,
                lineItems = listOf(lineItem),
                hostForceFullRefund = false,
                reasonText = "부분취소 전체 전환 — 활성 구간 소진",
            )
            return PartialCancelTxResult(
                events = fullCancelEvents,
                useCase = PartialCancelOrderUseCase.PartialCancelResult(
                    orderId = command.orderId,
                    refundAmount = Money.ZERO,
                    penaltyAmount = Money.ZERO,
                    remainingNights = 0,
                    convertedToFullCancel = true,
                ),
            )
        }

        // 부분 환불 (동기)
        val requestKey = buildRequestKey(command.orderId, cancelRange)
        val transaction = transactionDataService.findLatestPaidByBookingOrderId(command.orderId)
            ?: throw IllegalStateException("결제 내역을 찾을 수 없습니다: orderId=${command.orderId}")

        val refundAmount = lineItem.computePartialRefundAmount(cancelRange, domainResult.refundRatio)
        val cancelledAmount = lineItem.computeCancelledAmount(cancelRange)
        val penaltyAmount = Money(
            amount = cancelledAmount.amount - refundAmount.amount,
            currency = cancelledAmount.currency,
        )

        transactionDataService.refundPartialAndPersist(
            RefundTransactionUseCase.RefundPartialCommand(
                transactionId = transaction.transactionId,
                refundAmount = RefundAmount(
                    money = refundAmount,
                    reason = "부분취소 환불 — ${cancelRange.checkIn}~${cancelRange.checkOut}",
                ),
                requestKey = requestKey,
            )
        )

        // 이벤트 준비 (commit 후 발행)
        val event = BookingPartiallyCancelledEvent(
            bookingOrderId = command.orderId.toString(),
            lineItemId = command.lineItemId,
            accommodationId = lineItem.accommodationSnapshot.accommodationId,
            roomId = lineItem.roomSnapshot.roomId,
            cancelledCheckIn = cancelRange.checkIn,
            cancelledCheckOut = cancelRange.checkOut,
            requestKey = requestKey,
        )

        return PartialCancelTxResult(
            events = listOf(PendingEvent(KafkaTopics.BOOKING_EVENTS, event)),
            useCase = PartialCancelOrderUseCase.PartialCancelResult(
                orderId = command.orderId,
                refundAmount = refundAmount,
                penaltyAmount = penaltyAmount,
                remainingNights = domainResult.remainingNights,
                convertedToFullCancel = false,
            ),
        )
    }

    /**
     * 요청키 생성: `SHA-256(예약ID + 정렬된 취소 날짜 목록)`.
     * 부분취소 결제·이벤트 양쪽의 멱등성 보호에 사용된다.
     */
    private fun buildRequestKey(orderId: Long, range: DateRange): String {
        val dates = range.dates().sorted().joinToString(",")
        val raw = "$orderId:$dates"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(64)
    }

    data class PartialCancelTxResult(
        val events: List<PendingEvent>,
        val useCase: PartialCancelOrderUseCase.PartialCancelResult,
    )

    // ───────────────────── 미리보기 (read-only) ─────────────────────

    fun previewCancellation(orderId: Long): PreviewCancellationUseCase.CancellationPreview {
        val order = loadOrder(orderId)
        val lineItems = loadLineItems(orderId)
        val cancelable = order.status == BookingStatus.REQUESTED || order.status == BookingStatus.CONFIRMED
        val reason = if (cancelable) null else "현재 상태에서는 취소할 수 없습니다: ${order.status}"

        if (!cancelable) return emptyPreview(order.id, reason)
        if (order.status == BookingStatus.REQUESTED) return emptyPreview(order.id, null)

        val transaction = transactionDataService.findLatestPaidByBookingOrderId(order.id)
        if (transaction == null) {
            return PreviewCancellationUseCase.CancellationPreview(
                bookingOrderId = order.id,
                paidAmount = Money.ZERO,
                refundAmount = Money.ZERO,
                penaltyAmount = Money.ZERO,
                appliedRefundRatio = BigDecimal.ZERO,
                daysUntilCheckIn = lineItems.minOfOrNull { ChronoUnit.DAYS.between(LocalDate.now(), it.checkIn) } ?: 0,
                cancelable = true,
                reasonIfNotCancelable = "결제 내역이 없어 환불 없이 즉시 취소됩니다.",
            )
        }

        val refundAmount = computeRefundAmount(transaction.paidAmount, lineItems)
        val penalty = Money(
            amount = transaction.paidAmount.amount - refundAmount.amount,
            currency = transaction.paidAmount.currency,
        )
        val ratio = if (transaction.paidAmount.amount.signum() == 0) BigDecimal.ZERO
        else refundAmount.amount.divide(transaction.paidAmount.amount, 4, RoundingMode.HALF_UP)
        val earliestCheckIn = lineItems.minOf { it.checkIn }
        val days = ChronoUnit.DAYS.between(LocalDate.now(), earliestCheckIn)
        return PreviewCancellationUseCase.CancellationPreview(
            bookingOrderId = order.id,
            paidAmount = transaction.paidAmount,
            refundAmount = refundAmount,
            penaltyAmount = penalty,
            appliedRefundRatio = ratio,
            daysUntilCheckIn = days,
            cancelable = true,
            reasonIfNotCancelable = null,
        )
    }

    // ───────────────────── 내부 헬퍼 ─────────────────────

    private fun cancelUnpaidOrder(order: BookingOrder, lineItems: List<BookingOrderLineItem>) {
        val wasConfirmed = order.status == BookingStatus.CONFIRMED
        order.cancel()
        lineItems.forEach { lineItem ->
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

    private fun cancelConfirmedOrderInternal(
        order: BookingOrder,
        lineItems: List<BookingOrderLineItem>,
        hostForceFullRefund: Boolean,
        reasonText: String,
    ): List<PendingEvent> {
        val transaction = transactionDataService.findLatestPaidByBookingOrderId(order.id)
        if (transaction == null) {
            // 결제 레코드 없음 — 레거시 데이터 fallback. 즉시 취소.
            cancelUnpaidOrder(order, lineItems)
            return emptyList()
        }
        val refundAmount = if (hostForceFullRefund) {
            transaction.paidAmount
        } else {
            computeRefundAmount(transaction.paidAmount, lineItems)
        }
        val txEvent = transactionDataService.cancelWithPenaltyAndPersist(
            RefundTransactionUseCase.CancelWithPenaltyCommand(
                transactionId = transaction.transactionId,
                refundAmount = RefundAmount(money = refundAmount, reason = reasonText),
            )
        )
        // booking.cancel() 및 선점 해제는 TransactionFullyRefundedEvent 수신 컨슈머의 cancelFromTransactionEventTx 가 처리
        return listOf(PendingEvent(KafkaTopics.TRANSACTION_EVENTS, txEvent))
    }

    private fun computeRefundAmount(paidAmount: Money, lineItems: List<BookingOrderLineItem>): Money {
        require(lineItems.isNotEmpty()) { "LineItem 이 없는 예약은 취소할 수 없습니다." }
        val total = totalLineAmount(lineItems)
        val refundPerLine = lineItems.map { lineItem ->
            val lineShare = apportion(paidAmount, lineItem.lineTotal, total)
            val ratio = calculateCancellationRefundUseCase.calculateRefundRatio(
                CalculateCancellationRefundUseCase.CalculateRefundRatioCommand(
                    roomId = lineItem.roomSnapshot.roomId,
                    checkInDate = lineItem.checkIn,
                )
            ).appliedRefundRatio
            val refundForLine = lineShare.amount.multiply(ratio).setScale(0, RoundingMode.DOWN)
            Money(amount = refundForLine, currency = lineShare.currency)
        }
        val totalRefund = refundPerLine.fold(Money.ZERO) { acc, m -> acc + m }
        require(totalRefund.amount <= paidAmount.amount) {
            "환불 합계가 결제 금액을 초과합니다: $totalRefund > $paidAmount"
        }
        return totalRefund
    }

    private fun totalLineAmount(lineItems: List<BookingOrderLineItem>): Money =
        lineItems.fold(Money.ZERO) { acc, item -> acc + item.lineTotal }

    private fun apportion(paidAmount: Money, lineTotal: Money, total: Money): Money {
        if (total.amount.signum() == 0) return Money.ZERO
        val share = paidAmount.amount.multiply(lineTotal.amount).divide(total.amount, 0, RoundingMode.DOWN)
        return Money(amount = share, currency = paidAmount.currency)
    }

    private fun emptyPreview(orderId: Long, reason: String?) = PreviewCancellationUseCase.CancellationPreview(
        bookingOrderId = orderId,
        paidAmount = Money.ZERO,
        refundAmount = Money.ZERO,
        penaltyAmount = Money.ZERO,
        appliedRefundRatio = BigDecimal.ZERO,
        daysUntilCheckIn = 0,
        cancelable = reason == null,
        reasonIfNotCancelable = reason,
    )

    data class PlaceOrderResult(
        val order: BookingOrder,
        val events: List<PendingEvent>,
    )
}
