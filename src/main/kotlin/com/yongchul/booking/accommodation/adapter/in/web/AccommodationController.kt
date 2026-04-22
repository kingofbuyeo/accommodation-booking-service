package com.yongchul.booking.accommodation.adapter.`in`.web

import com.yongchul.booking.accommodation.adapter.`in`.web.dto.AccommodationResponse
import com.yongchul.booking.accommodation.application.port.`in`.BlockRoomScheduleUseCase
import com.yongchul.booking.accommodation.application.port.`in`.RegisterAccommodationUseCase
import com.yongchul.booking.accommodation.application.service.AccommodationService
import com.yongchul.booking.accommodation.domain.RoomScheduleType
import com.yongchul.booking.accommodation.domain.vo.PreemptionPolicy
import com.yongchul.booking.common.Money
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/accommodations")
class AccommodationController(
    private val registerAccommodationUseCase: RegisterAccommodationUseCase,
    private val blockRoomScheduleUseCase: BlockRoomScheduleUseCase,
    private val accommodationService: AccommodationService,
) {
    @GetMapping
    fun listAll(): ResponseEntity<List<AccommodationResponse>> {
        val accommodations = accommodationService.listAllAccommodations()
        if (accommodations.isEmpty()) return ResponseEntity.ok(emptyList())
        val rooms = accommodationService.loadRoomsForAccommodationIds(accommodations.map { it.id })
        val roomIds = rooms.map { it.id }
        val schedulesByRoomId = accommodationService.loadSchedulesForRoomIds(roomIds)
        val bookedDatesByRoomId = accommodationService.loadBookedDatesForRoomIds(roomIds)
        val roomsByAccId = rooms.groupBy { it.accommodationId }
        return ResponseEntity.ok(accommodations.map { acc ->
            AccommodationResponse.from(acc, roomsByAccId[acc.id] ?: emptyList(), schedulesByRoomId, bookedDatesByRoomId)
        })
    }

    @GetMapping("/{accommodationId}")
    fun getOne(@PathVariable accommodationId: Long): ResponseEntity<AccommodationResponse> {
        val accommodation = accommodationService.loadAccommodation(accommodationId)
        val rooms = accommodationService.loadRoomsForAccommodationIds(listOf(accommodationId))
        val roomIds = rooms.map { it.id }
        val schedulesByRoomId = accommodationService.loadSchedulesForRoomIds(roomIds)
        val bookedDatesByRoomId = accommodationService.loadBookedDatesForRoomIds(roomIds)
        return ResponseEntity.ok(AccommodationResponse.from(accommodation, rooms, schedulesByRoomId, bookedDatesByRoomId))
    }
    @PostMapping
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Long> {
        val accommodation = registerAccommodationUseCase.register(
            RegisterAccommodationUseCase.RegisterAccommodationCommand(
                name = request.name,
                address = request.address,
                description = request.description,
                hostName = request.hostName,
                preemptionPolicy = request.toPreemptionPolicy(),
            )
        )
        return ResponseEntity.ok(accommodation.id)
    }

    @PostMapping("/{accommodationId}/rooms")
    fun addRoom(
        @PathVariable accommodationId: Long,
        @RequestBody request: AddRoomRequest,
    ): ResponseEntity<Unit> {
        registerAccommodationUseCase.addRoom(
            RegisterAccommodationUseCase.AddRoomCommand(
                accommodationId = accommodationId,
                roomName = request.roomName,
                capacity = request.capacity,
                pricePerNight = Money.of(request.pricePerNight),
                preemptionPolicy = request.toPreemptionPolicy(),
            )
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{accommodationId}/rooms/{roomId}/schedules/block")
    fun blockSchedule(
        @PathVariable accommodationId: Long,
        @PathVariable roomId: Long,
        @RequestBody request: BlockScheduleRequest,
    ): ResponseEntity<Unit> {
        blockRoomScheduleUseCase.block(
            BlockRoomScheduleUseCase.BlockCommand(
                accommodationId = accommodationId,
                roomId = roomId,
                date = request.date,
                type = request.type,
                reason = request.reason,
            )
        )
        return ResponseEntity.ok().build()
    }

    data class RegisterRequest(
        val name: String,
        val address: String,
        val description: String?,
        val hostName: String,
        val shortLeadTimeDays: Int = 3,
        val shortLeadTimeTtlMinutes: Long = 20,
        val longLeadTimeDays: Int = 30,
        val longLeadTimeTtlMinutes: Long = 120,
        val defaultTtlMinutes: Long = 60,
    ) {
        fun toPreemptionPolicy() = PreemptionPolicy(
            shortLeadTimeDays = shortLeadTimeDays,
            shortLeadTimeTtlMinutes = shortLeadTimeTtlMinutes,
            longLeadTimeDays = longLeadTimeDays,
            longLeadTimeTtlMinutes = longLeadTimeTtlMinutes,
            defaultTtlMinutes = defaultTtlMinutes,
        )
    }

    data class AddRoomRequest(
        val roomName: String,
        val capacity: Int,
        val pricePerNight: Long,
        val shortLeadTimeDays: Int = 3,
        val shortLeadTimeTtlMinutes: Long = 20,
        val longLeadTimeDays: Int = 30,
        val longLeadTimeTtlMinutes: Long = 120,
        val defaultTtlMinutes: Long = 60,
    ) {
        fun toPreemptionPolicy() = PreemptionPolicy(
            shortLeadTimeDays = shortLeadTimeDays,
            shortLeadTimeTtlMinutes = shortLeadTimeTtlMinutes,
            longLeadTimeDays = longLeadTimeDays,
            longLeadTimeTtlMinutes = longLeadTimeTtlMinutes,
            defaultTtlMinutes = defaultTtlMinutes,
        )
    }

    @PostMapping("/{accommodationId}/rooms/{roomId}/schedules/block/bulk")
    fun blockScheduleBulk(
        @PathVariable accommodationId: Long,
        @PathVariable roomId: Long,
        @RequestBody request: BulkBlockScheduleRequest,
    ): ResponseEntity<Unit> {
        blockRoomScheduleUseCase.blockBulk(
            BlockRoomScheduleUseCase.BulkBlockCommand(
                accommodationId = accommodationId,
                roomId = roomId,
                dates = request.dates,
                type = request.type,
                reason = request.reason,
            )
        )
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{accommodationId}/rooms/{roomId}/schedules/block/{date}")
    fun unblockSchedule(
        @PathVariable accommodationId: Long,
        @PathVariable roomId: Long,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): ResponseEntity<Unit> {
        blockRoomScheduleUseCase.unblock(
            BlockRoomScheduleUseCase.UnblockCommand(
                accommodationId = accommodationId,
                roomId = roomId,
                date = date,
            )
        )
        return ResponseEntity.noContent().build()
    }

    data class BlockScheduleRequest(val date: LocalDate, val type: RoomScheduleType, val reason: String?)

    data class BulkBlockScheduleRequest(
        val dates: List<LocalDate>,
        val type: RoomScheduleType,
        val reason: String?,
    )
}
