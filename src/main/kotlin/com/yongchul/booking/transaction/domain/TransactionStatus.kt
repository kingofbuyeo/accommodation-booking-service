package com.yongchul.booking.transaction.domain

enum class TransactionStatus {
    /** 결제 요청됨 */
    PENDING,

    /** 결제 완료 */
    PAID,

    /** 결제 실패 — TTL 만료 전 신규 Transaction으로 재시도 가능 */
    FAILED,

    /** 결제 취소 — Booking EXPIRED 확인 후 시스템이 자동 환불 처리 */
    CANCELLED,

    /** 전체 취소 — 고객 요청 취소 시 페널티 차감 후 잔액 환불, 한 번의 환불 row로 종료 */
    FULLY_CANCELLED,
}
