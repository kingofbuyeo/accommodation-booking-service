package com.yongchul.booking.booking.domain

enum class BookingStatus {
    /** 예약 요청됨 (선점 완료, 결제 대기) */
    REQUESTED,

    /** 예약 확정됨 (결제 완료) */
    CONFIRMED,

    /** 체크인 완료 */
    CHECKED_IN,

    /** 체크아웃 완료 */
    CHECKED_OUT,

    /** 취소됨 */
    CANCELLED,

    /** 선점 TTL 만료 후 결제 완료 — 최종 상태, 결제 컨텍스트가 취소 처리 */
    EXPIRED,
}
