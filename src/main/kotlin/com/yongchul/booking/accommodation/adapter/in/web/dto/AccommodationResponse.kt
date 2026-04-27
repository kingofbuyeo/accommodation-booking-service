package com.yongchul.booking.accommodation.adapter.`in`.web.dto

import com.yongchul.booking.accommodation.domain.Accommodation
import com.yongchul.booking.accommodation.domain.Room
import com.yongchul.booking.accommodation.domain.RoomSchedule
import com.yongchul.booking.accommodation.domain.RoomScheduleType
import com.yongchul.booking.accommodation.domain.vo.AccommodationOperationPolicy
import java.math.BigDecimal
import java.time.LocalDate

data class AccommodationResponse(
    val id: Long,
    val name: String,
    val address: String,
    val description: String?,
    val hostName: String,
    val operationPolicy: PolicyResponse,
    /** ISO LocalTime 문자열 (예: "15:00") */
    val checkInTime: String,
    val rooms: List<RoomResponse>,
) {
    data class RoomResponse(
        val id: Long,
        val name: String,
        val capacity: Int,
        val pricePerNight: BigDecimal,
        val currency: String,
        val operationPolicy: PolicyResponse,
        val blockedDates: List<BlockedDate>,
        val bookedDates: List<LocalDate>,
    )

    data class BlockedDate(
        val date: LocalDate,
        val type: RoomScheduleType,
        val reason: String?,
    )

    data class CancellationPenaltyTierResponse(
        val minDaysToCheckIn: Int,
        val refundRatio: BigDecimal,
    )

    data class PolicyResponse(
        val shortLeadTimeDays: Int,
        val shortLeadTimeTtlMinutes: Long,
        val longLeadTimeDays: Int,
        val longLeadTimeTtlMinutes: Long,
        val defaultTtlMinutes: Long,
        val cancellationPenaltyTiers: List<CancellationPenaltyTierResponse>,
    )

    companion object {
        fun from(
            accommodation: Accommodation,
            rooms: List<Room>,
            schedulesByRoomId: Map<Long, List<RoomSchedule>>,
            bookedDatesByRoomId: Map<Long, List<LocalDate>> = emptyMap(),
        ) = AccommodationResponse(
            id = accommodation.id,
            name = accommodation.name,
            address = accommodation.address,
            description = accommodation.description,
            hostName = accommodation.hostName,
            operationPolicy = accommodation.operationPolicy.toResponse(),
            checkInTime = accommodation.effectiveCheckInTime().toString(),
            rooms = rooms.map { room ->
                RoomResponse(
                    id = room.id,
                    name = room.name,
                    capacity = room.capacity,
                    pricePerNight = room.pricePerNight.amount,
                    currency = room.pricePerNight.currency,
                    operationPolicy = room.operationPolicy.toResponse(),
                    blockedDates = (schedulesByRoomId[room.id] ?: emptyList()).map { s ->
                        BlockedDate(date = s.blockedDate, type = s.type, reason = s.reason)
                    },
                    bookedDates = bookedDatesByRoomId[room.id] ?: emptyList(),
                )
            },
        )

        private fun AccommodationOperationPolicy?.toResponse(): PolicyResponse {
            val p = this ?: AccommodationOperationPolicy()
            return PolicyResponse(
                shortLeadTimeDays = p.effectiveShortLeadTimeDays,
                shortLeadTimeTtlMinutes = p.effectiveShortLeadTimeTtlMinutes,
                longLeadTimeDays = p.effectiveLongLeadTimeDays,
                longLeadTimeTtlMinutes = p.effectiveLongLeadTimeTtlMinutes,
                defaultTtlMinutes = p.effectiveDefaultTtlMinutes,
                cancellationPenaltyTiers = p.effectiveCancellationPenaltyTiers.map {
                    CancellationPenaltyTierResponse(
                        minDaysToCheckIn = it.minDaysToCheckIn,
                        refundRatio = it.refundRatio,
                    )
                },
            )
        }
    }
}