package com.yongchul.booking.transaction.domain

enum class TransactionDetailType {
    /** 결제 원장 */
    PAYMENT,

    /** 전액/페널티 차감 환불 원장 (전체취소) */
    REFUND,

    /** Week3 신규: 부분취소 환불 원장. requestKey 가 채워져야 함. */
    PARTIAL_REFUND,
}
