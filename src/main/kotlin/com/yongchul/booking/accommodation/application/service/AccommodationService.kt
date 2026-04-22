package com.yongchul.booking.accommodation.application.service

import com.yongchul.booking.accommodation.adapter.out.persistence.AccommodationJpaRepository
import com.yongchul.booking.accommodation.adapter.out.persistence.ConfirmedBookingDateJpaRepository
import com.yongchul.booking.accommodation.adapter.out.persistence.RoomJpaRepository
import com.yongchul.booking.accommodation.adapter.out.persistence.RoomScheduleJpaRepository
import com.yongchul.booking.accommodation.application.port.`in`.BlockRoomScheduleUseCase
import com.yongchul.booking.accommodation.application.port.`in`.RegisterAccommodationUseCase
import com.yongchul.booking.accommodation.domain.Accommodation
import com.yongchul.booking.accommodation.domain.Room
import com.yongchul.booking.accommodation.domain.RoomSchedule
import com.yongchul.booking.common.DateRange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class AccommodationService(
    private val accommodationJpaRepository: AccommodationJpaRepository,
    private val roomJpaRepository: RoomJpaRepository,
    private val roomScheduleJpaRepository: RoomScheduleJpaRepository,
    private val confirmedBookingDateJpaRepository: ConfirmedBookingDateJpaRepository,
) : RegisterAccommodationUseCase, BlockRoomScheduleUseCase {

    fun listAllAccommodations(): List<Accommodation> = accommodationJpaRepository.findAll()

    fun loadAccommodation(id: Long): Accommodation =
        accommodationJpaRepository.findById(id).orElseThrow {
            NoSuchElementException("숙소를 찾을 수 없습니다: id=$id")
        }

    fun loadRoom(accommodationId: Long, roomId: Long): Room =
        roomJpaRepository.findByIdAndAccommodationId(roomId, accommodationId)
            ?: throw NoSuchElementException("방을 찾을 수 없습니다: roomId=$roomId")

    fun loadRoomsForAccommodationIds(accommodationIds: List<Long>): List<Room> =
        if (accommodationIds.isEmpty()) emptyList()
        else roomJpaRepository.findByAccommodationIdIn(accommodationIds)

    fun loadSchedulesForRoomIds(roomIds: List<Long>): Map<Long, List<RoomSchedule>> =
        if (roomIds.isEmpty()) emptyMap()
        else roomScheduleJpaRepository.findByRoomIdIn(roomIds).groupBy { it.roomId }

    fun loadBookedDatesForRoomIds(roomIds: List<Long>): Map<Long, List<LocalDate>> =
        if (roomIds.isEmpty()) emptyMap()
        else confirmedBookingDateJpaRepository.findByRoomIdIn(roomIds)
            .groupBy({ it.roomId }, { it.reservedDate })

    fun isRoomAvailableFor(roomId: Long, dateRange: DateRange): Boolean {
        val schedules = roomScheduleJpaRepository.findByRoomId(roomId)
        return schedules.none { dateRange.contains(it.blockedDate) }
    }

    @Transactional
    override fun register(command: RegisterAccommodationUseCase.RegisterAccommodationCommand): Accommodation =
        accommodationJpaRepository.save(
            Accommodation(
                name = command.name,
                address = command.address,
                description = command.description,
                hostName = command.hostName,
                preemptionPolicy = command.preemptionPolicy,
            )
        )

    @Transactional
    override fun addRoom(command: RegisterAccommodationUseCase.AddRoomCommand): Room {
        require(accommodationJpaRepository.existsById(command.accommodationId)) {
            "숙소를 찾을 수 없습니다: id=${command.accommodationId}"
        }
        return roomJpaRepository.save(
            Room(
                accommodationId = command.accommodationId,
                name = command.roomName,
                capacity = command.capacity,
                pricePerNight = command.pricePerNight,
                preemptionPolicy = command.preemptionPolicy,
            )
        )
    }

    @Transactional
    override fun block(command: BlockRoomScheduleUseCase.BlockCommand): RoomSchedule {
        require(roomJpaRepository.existsByIdAndAccommodationId(command.roomId, command.accommodationId)) {
            "방을 찾을 수 없습니다: roomId=${command.roomId}"
        }
        require(!roomScheduleJpaRepository.existsByRoomIdAndBlockedDate(command.roomId, command.date)) {
            "이미 차단된 날짜입니다: ${command.date}"
        }
        return roomScheduleJpaRepository.save(
            RoomSchedule(roomId = command.roomId, blockedDate = command.date, type = command.type, reason = command.reason)
        )
    }

    @Transactional
    override fun blockBulk(command: BlockRoomScheduleUseCase.BulkBlockCommand): List<RoomSchedule> {
        require(roomJpaRepository.existsByIdAndAccommodationId(command.roomId, command.accommodationId)) {
            "방을 찾을 수 없습니다: roomId=${command.roomId}"
        }
        require(command.dates.isNotEmpty()) { "차단할 날짜가 없습니다." }

        val alreadyBlocked = command.dates.filter {
            roomScheduleJpaRepository.existsByRoomIdAndBlockedDate(command.roomId, it)
        }
        require(alreadyBlocked.isEmpty()) {
            "이미 차단된 날짜가 포함되어 있습니다: $alreadyBlocked"
        }

        return roomScheduleJpaRepository.saveAll(
            command.dates.map { date ->
                RoomSchedule(roomId = command.roomId, blockedDate = date, type = command.type, reason = command.reason)
            }
        )
    }

    @Transactional
    override fun unblock(command: BlockRoomScheduleUseCase.UnblockCommand) {
        roomScheduleJpaRepository.deleteByRoomIdAndBlockedDate(command.roomId, command.date)
    }
}
