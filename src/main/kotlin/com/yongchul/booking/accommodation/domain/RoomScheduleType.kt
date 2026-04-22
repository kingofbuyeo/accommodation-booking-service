package com.yongchul.booking.accommodation.domain

enum class RoomScheduleType {
    /** 호스트가 수동으로 차단한 날짜 */
    BLOCKED,

    /** 점검/청소 등 운영상 사용 불가 */
    MAINTENANCE,

    /** 호스트 자체 사용 */
    OWNER_USE,
}