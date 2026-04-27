package com.yongchul.booking.accommodation.application.port.`in`

import com.yongchul.booking.accommodation.domain.Accommodation
import com.yongchul.booking.accommodation.domain.Room
import com.yongchul.booking.accommodation.domain.RoomSchedule
import com.yongchul.booking.accommodation.domain.RoomScheduleType
import com.yongchul.booking.accommodation.domain.vo.AccommodationOperationPolicy
import com.yongchul.booking.common.Money
import java.time.LocalDate
import java.time.LocalTime

interface RegisterAccommodationUseCase {
    fun register(command: RegisterAccommodationCommand): Accommodation
    fun addRoom(command: AddRoomCommand): Room

    data class RegisterAccommodationCommand(
        val name: String,
        val address: String,
        val description: String?,
        val hostName: String,
        val operationPolicy: AccommodationOperationPolicy = AccommodationOperationPolicy(),
        val checkInTime: LocalTime? = null,
    )

    data class AddRoomCommand(
        val accommodationId: Long,
        val roomName: String,
        val capacity: Int,
        val pricePerNight: Money,
        val operationPolicy: AccommodationOperationPolicy = AccommodationOperationPolicy(),
    )
}

interface BlockRoomScheduleUseCase {
    fun block(command: BlockCommand): RoomSchedule
    fun blockBulk(command: BulkBlockCommand): List<RoomSchedule>
    fun unblock(command: UnblockCommand)

    data class BlockCommand(
        val accommodationId: Long,
        val roomId: Long,
        val date: LocalDate,
        val type: RoomScheduleType,
        val reason: String? = null,
    )

    data class BulkBlockCommand(
        val accommodationId: Long,
        val roomId: Long,
        val dates: List<LocalDate>,
        val type: RoomScheduleType,
        val reason: String? = null,
    )

    data class UnblockCommand(
        val accommodationId: Long,
        val roomId: Long,
        val date: LocalDate,
    )
}