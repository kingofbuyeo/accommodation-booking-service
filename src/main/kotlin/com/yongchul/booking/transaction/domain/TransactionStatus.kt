package com.yongchul.booking.transaction.domain

enum class TransactionStatus {
    /** 결제 요청됨 */
    PENDING,

    /** 결제 완료 */
    PAID,

    /** 결제 실패 — TTL 만료 전 신규 Transaction으로 재시도 가능 */
    FAILED,

    /** 결제 취소 — Booking EXPIRED 확인 후 환불 처리 */
    CANCELLED,

    /** 부분 취소 — TransactionDetail에 환불 row 추가 */
    PARTIAL_CANCELLED,

    /** 전체 취소 — 잔여 결제 금액이 0이 되는 시점 */
    FULLY_CANCELLED,
}
