package com.yongchul.booking.booking.domain.vo

import com.yongchul.booking.common.Money
import java.time.LocalDate

/**
 * 예약 시점 1박 가격 스냅샷.
 *
 * 부분취소 환불 금액 계산의 기준이 되는 박제값으로, 호스트가 사후에 가격을 바꿔도
 * 이 값은 영향받지 않는다. List<DailyPrice> 형태로 [com.yongchul.booking.booking.domain.BookingOrderLineItem]
 * 의 JSON 컬럼에 저장된다.
 */
data class DailyPrice(
    val date: LocalDate,
    val amount: Money,
)