package com.yongchul.booking.accommodation.application.port.`in`

import com.yongchul.booking.common.DateRange

interface SchedulePreemptionUseCase {
    fun preempt(command: PreemptCommand): Boolean
    fun confirmPreemption(command: ConfirmPreemptionCommand)
    fun release(command: ReleaseCommand)
    fun releaseConfirmed(command: ReleaseConfirmedCommand)

    data class PreemptCommand(
        val accommodationId: Long,
        val roomId: Long,
        val dateRange: DateRange,
        val bookingOrderId: Long,
    )

    data class ConfirmPreemptionCommand(
        val accommodationId: Long,
        val roomId: Long,
        val dateRange: DateRange,
        val bookingOrderId: Long,
    )

    data class ReleaseCommand(
        val accommodationId: Long,
        val roomId: Long,
        val dateRange: DateRange,
    )

    data class ReleaseConfirmedCommand(
        val accommodationId: Long,
        val roomId: Long,
        val dateRange: DateRange,
        val bookingOrderId: Long,
    )
}
