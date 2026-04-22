package com.yongchul.booking.accommodation.application.service

import com.yongchul.booking.accommodation.adapter.out.persistence.ConfirmedBookingDateJpaRepository
import com.yongchul.booking.accommodation.application.port.`in`.SchedulePreemptionUseCase
import com.yongchul.booking.accommodation.application.port.out.AccommodationPort
import com.yongchul.booking.accommodation.application.port.out.SchedulePreemptionPort
import com.yongchul.booking.accommodation.domain.ConfirmedBookingDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SchedulePreemptionService(
    private val schedulePreemptionPort: SchedulePreemptionPort,
    private val accommodationPort: AccommodationPort,
    private val confirmedBookingDateJpaRepository: ConfirmedBookingDateJpaRepository,
) : SchedulePreemptionUseCase {

    // 선점 흐름: 1) DB 확정 예약 충돌 확인 (Redis 캐시 미스 대비) 2) 충돌 없으면 Redis SETNX 시도
    // TTL은 숙소의 PreemptionPolicy가 체크인 일자 기반으로 계산
    override fun preempt(command: SchedulePreemptionUseCase.PreemptCommand): Boolean {
        val requestedDates = command.dateRange.dates()

        val conflicts = confirmedBookingDateJpaRepository
            .findByRoomIdAndReservedDateIn(command.roomId, requestedDates)
        if (conflicts.isNotEmpty()) {
            schedulePreemptionPort.repopulate(conflicts)
            return false
        }

        val room = accommodationPort.findRoomById(command.roomId)
        val ttl = room.calculatePreemptionTtl(command.dateRange.checkIn)

        return schedulePreemptionPort.preempt(
            accommodationId = command.accommodationId,
            roomId = command.roomId,
            dateRange = command.dateRange,
            bookingOrderId = command.bookingOrderId.toString(),
            ttl = ttl,
        )
    }

    @Transactional
    override fun confirmPreemption(command: SchedulePreemptionUseCase.ConfirmPreemptionCommand) {
        val records = command.dateRange.dates().map { date ->
            ConfirmedBookingDate(
                accommodationId = command.accommodationId,
                roomId = command.roomId,
                reservedDate = date,
                bookingOrderId = command.bookingOrderId,
            )
        }
        confirmedBookingDateJpaRepository.saveAll(records)

        schedulePreemptionPort.promoteToConfirmed(
            accommodationId = command.accommodationId,
            roomId = command.roomId,
            dateRange = command.dateRange,
            bookingOrderId = command.bookingOrderId.toString(),
        )
    }

    override fun release(command: SchedulePreemptionUseCase.ReleaseCommand) {
        schedulePreemptionPort.release(command.accommodationId, command.roomId, command.dateRange)
    }

    @Transactional
    override fun releaseConfirmed(command: SchedulePreemptionUseCase.ReleaseConfirmedCommand) {
        confirmedBookingDateJpaRepository.deleteByBookingOrderId(command.bookingOrderId)
        schedulePreemptionPort.release(command.accommodationId, command.roomId, command.dateRange)
    }
}
