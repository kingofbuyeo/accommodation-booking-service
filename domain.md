# Domain Model — 숙박 예약 서비스

이 문서는 BE 서비스(`accommodation-booking-service`)의 도메인 모델 전체를 시각화한다.
Week1 ~ Week3 의 결정과 코드 베이스를 기준으로 작성되었으며, 모든 다이어그램은 Mermaid 로 표현되어 GitHub/IDE 에서 직접 렌더링된다.

---

## 1. 개요

도메인은 **3개의 Bounded Context** 로 분리되어 있고, 컨텍스트 간 통신은 **Kafka 도메인 이벤트** + **read-only Use Case 포트(in-port) 호출** 을 통해 이루어진다.
컨텍스트 간 직접 엔티티 참조는 없으며 모든 cross-context 식별자는 `Long` ID 또는 ID 스냅샷(`AccommodationSnapshot`, `RoomSnapshot`)으로만 전달된다.

| 컨텍스트 | 책임 | 핵심 Aggregate Root |
|----------|------|---------------------|
| **Booking** | 예약 주문 생성/상태 전이/취소·환불 오케스트레이션 | `BookingOrder` |
| **Accommodation** | 숙소·방 정보, 선점·취소 페널티 정책, 일정(차단/확정) 관리 | `Accommodation`, `Room` |
| **Transaction** | 결제 처리, 환불 원장(append-only) 관리, 결제 상태 전이 | `Transaction` |

---

## 2. Bounded Context Map

```mermaid
flowchart LR
    subgraph BookingCtx[Booking Context]
        BO[BookingOrder<br/>«Aggregate Root»]
        BLI[BookingOrderLineItem<br/>«Entity»]
    end

    subgraph AccommodationCtx[Accommodation Context]
        AC[Accommodation<br/>«Aggregate Root»]
        RM[Room<br/>«Aggregate Root»]
        RS[RoomSchedule<br/>«Entity»]
        CBD[ConfirmedBookingDate<br/>«Entity»]
        PF[PreemptionFallback<br/>«Entity»]
    end

    subgraph TransactionCtx[Transaction Context]
        TX[Transaction<br/>«Aggregate Root»]
        TD[TransactionDetail<br/>«Entity»<br/>append-only]
    end

    BO -.포함.-> BLI
    AC -.포함.-> RM
    RM -.소유.-> RS
    AC -.소유.-> CBD
    AC -.소유.-> PF
    TX -.포함.-> TD

    BookingCtx -- "예약 시점 스냅샷<br/>(AccommodationSnapshot,<br/>RoomSnapshot 복사)" --> AccommodationCtx
    BookingCtx -- "선점/확정 요청<br/>(SchedulePreemptionUseCase)" --> AccommodationCtx
    BookingCtx -- "환불 비율 산출 요청<br/>(CalculateCancellationRefundUseCase)" --> AccommodationCtx
    BookingCtx -- "결제 모사 / 취소 환불<br/>(InitiateTransactionUseCase,<br/>RefundTransactionUseCase)" --> TransactionCtx

    AccommodationCtx -- "TransactionCompletedEvent 수신<br/>→ 선점 확정 또는 결제 자동 취소" --> TransactionCtx
    BookingCtx -- "AccommodationDomainEvent 수신<br/>(SchedulePreemptionExpired,<br/>BookingConfirmed)" --> AccommodationCtx
    BookingCtx -- "TransactionFullyRefunded 수신<br/>→ Booking CANCELLED 전이" --> TransactionCtx

    classDef ctx fill:#dbeafe,stroke:#3b82f6,color:#1e3a8a
    class BookingCtx,AccommodationCtx,TransactionCtx ctx
```

**경계 원칙**
- 컨텍스트 간 ID 참조만 허용 — `BookingOrderLineItem` 은 `accommodationId`, `roomId` 를 스냅샷 VO 로 들고 있을 뿐 Accommodation 엔티티를 직접 참조하지 않는다.
- Transaction 은 `Transaction.bookingOrderId` 로 Booking 을 식별 (역참조 X).
- Accommodation 의 `ConfirmedBookingDate` / `PreemptionFallback` 은 `bookingOrderId` 를 들고 있으나 Booking 엔티티 직접 참조 X.

---

## 3. Aggregate 클래스 다이어그램

### 3.1 Booking Context

```mermaid
classDiagram
    class BookingOrder {
        «Aggregate Root»
        +Long id
        +GuestInfo guestInfo
        +BookingStatus status
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        +LocalDateTime? expiresAt
        +confirm()
        +expire()
        +cancel()
        +checkIn()
        +checkOut()
    }

    class BookingOrderLineItem {
        «Entity»
        +Long id
        +Long bookingOrderId
        +AccommodationSnapshot accommodationSnapshot
        +RoomSnapshot roomSnapshot
        +DateRange dateRange
        +Money lineTotal
        +Int nights
    }

    class GuestInfo {
        «Value Object»
        +String guestName
        +String phone
        +Int headcount
    }

    class AccommodationSnapshot {
        «Value Object»
        +Long accommodationId
        +String accommodationName
        +String address
        +String hostName
    }

    class RoomSnapshot {
        «Value Object»
        +Long roomId
        +String roomName
        +Int capacity
        +Money pricePerNightAtBooking
    }

    class DateRange {
        «Value Object»
        +LocalDate checkIn
        +LocalDate checkOut
        +nights() Int
        +contains(date) Boolean
        +overlaps(other) Boolean
    }

    class BookingStatus {
        «Enum»
        REQUESTED
        CONFIRMED
        CHECKED_IN
        CHECKED_OUT
        CANCELLED
        EXPIRED
    }

    BookingOrder *-- GuestInfo
    BookingOrder ..> BookingStatus
    BookingOrderLineItem *-- AccommodationSnapshot
    BookingOrderLineItem *-- RoomSnapshot
    BookingOrderLineItem *-- DateRange
    BookingOrder "1" o-- "1..*" BookingOrderLineItem : bookingOrderId 참조
```

**불변조건** (`BookingOrder` 메서드가 보호):
- `취소된 예약은 재확정할 수 없다` — `confirm()` 이 `REQUESTED` 만 허용
- `체크인 이후 취소할 수 없다` — `cancel()` 이 `CHECKED_IN`/`CHECKED_OUT` 거부
- `체크인 없이 체크아웃할 수 없다` — `checkOut()` 이 `CHECKED_IN` 만 허용
- `만료된 예약은 확정할 수 없다` — `expire()` 후 `confirm()` 차단

### 3.2 Accommodation Context

```mermaid
classDiagram
    class Accommodation {
        «Aggregate Root»
        +Long id
        +String name
        +String address
        +String? description
        +String hostName
        +AccommodationOperationPolicy? operationPolicy
        +LocalTime? checkInTime
        +calculatePreemptionTtl(checkInDate) Duration
        +effectiveCheckInTime() LocalTime
        +resolvedPolicy() AccommodationOperationPolicy
    }

    class Room {
        «Aggregate Root»
        +Long id
        +Long accommodationId
        +String name
        +Int capacity
        +Money pricePerNight
        +AccommodationOperationPolicy? operationPolicy
        +isAvailableFor(schedules, dateRange) Boolean
        +calculateTotalPrice(dateRange) Money
        +calculatePreemptionTtl(checkInDate) Duration
        +resolvedPolicy() AccommodationOperationPolicy
    }

    class RoomSchedule {
        «Entity»
        +Long id
        +Long roomId
        +LocalDate blockedDate
        +RoomScheduleType type
        +String? reason
    }

    class ConfirmedBookingDate {
        «Entity»
        +Long id
        +Long accommodationId
        +Long roomId
        +LocalDate reservedDate
        +Long bookingOrderId
    }

    class PreemptionFallback {
        «Entity»
        +Long id
        +Long accommodationId
        +Long roomId
        +LocalDate reservedDate
        +Long bookingOrderId
        +LocalDateTime expiresAt
    }

    class AccommodationOperationPolicy {
        «Value Object»
        +Int? shortLeadTimeDays
        +Long? shortLeadTimeTtlMinutes
        +Int? longLeadTimeDays
        +Long? longLeadTimeTtlMinutes
        +Long? defaultTtlMinutes
        +List~CancellationPenaltyTier~? cancellationPenaltyTiers
        +calculateTtl(checkInDate) Duration
        +resolveRefundRatio(checkInDate, requestedAt) BigDecimal
    }

    class CancellationPenaltyTier {
        «Value Object»
        +Int minDaysToCheckIn
        +BigDecimal refundRatio
    }

    class SchedulePreemptionKey {
        «Value Object»
        +Long accommodationId
        +Long roomId
        +LocalDate date
        +toRedisKey() String
    }

    class RoomScheduleType {
        «Enum»
        BLOCKED
        MAINTENANCE
        OWNER_USE
    }

    Accommodation *-- AccommodationOperationPolicy
    Room *-- AccommodationOperationPolicy
    AccommodationOperationPolicy "1" *-- "0..*" CancellationPenaltyTier
    Room "1" o-- "0..*" RoomSchedule : roomId 참조
    Accommodation "1" o-- "0..*" ConfirmedBookingDate : accommodationId 참조
    Accommodation "1" o-- "0..*" PreemptionFallback : accommodationId 참조
    RoomSchedule ..> RoomScheduleType
    Accommodation ..> SchedulePreemptionKey : 선점 키 발행
```

**핵심 정책**
- `AccommodationOperationPolicy.cancellationPenaltyTiers` 는 JSON 으로 직렬화되어 `cancellation_penalty_tiers TEXT` 컬럼에 저장 (`CancellationPenaltyTiersConverter`)
- `CancellationPenaltyTier.refundRatio` 는 `0.5 ~ 1.0` 강제 (페널티 최대 50% 불변조건)
- `ConfirmedBookingDate` 는 `(room_id, reserved_date)` Unique 로 더블 부킹 방지
- `PreemptionFallback` 은 Redis 장애 시 race 차단용 — 동일 unique constraint
- `RoomSchedule` 은 `(room_id, blocked_date)` Unique

### 3.3 Transaction Context

```mermaid
classDiagram
    class Transaction {
        «Aggregate Root»
        +Long id
        +Long bookingOrderId
        +TransactionStatus status
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        +complete(ledgerInfo) TransactionDetail
        +fail()
        +cancel(currentDetails, reason) TransactionDetail
        +cancelWithPenalty(currentDetails, refundAmount) TransactionDetail
    }

    class TransactionDetail {
        «Entity, append-only»
        +Long id
        +Long transactionId
        +TransactionDetailType type
        +LedgerInfo? ledgerInfo
        +RefundAmount? refundAmount
        +LocalDateTime createdAt
    }

    class LedgerInfo {
        «Value Object»
        +String pgTransactionId
        +String approvalNumber
        +String pgName
        +Money paidAmount
    }

    class RefundAmount {
        «Value Object»
        +Money money
        +String reason
    }

    class TransactionStatus {
        «Enum»
        PENDING
        PAID
        FAILED
        CANCELLED
        FULLY_CANCELLED
    }

    class TransactionDetailType {
        «Enum»
        PAYMENT
        REFUND
    }

    Transaction ..> TransactionStatus
    Transaction "1" o-- "1..*" TransactionDetail : transactionId 참조
    TransactionDetail ..> TransactionDetailType
    TransactionDetail *-- LedgerInfo
    TransactionDetail *-- RefundAmount
```

**불변조건** (`Transaction` 메서드가 보호):
- `TransactionDetail` 은 append-only — 생성 후 수정 금지
- `cancel(reason)` 은 `PAID` 상태에서만 → `CANCELLED` (시스템 자동 취소 경로)
- `cancelWithPenalty(refundAmount)` 는 `PAID` 상태에서만 → 즉시 `FULLY_CANCELLED` (고객 취소 경로, 한 번의 환불 row 추가)
- 페널티 잔액(= paidAmount − refundAmount) 은 호스트 몫으로 남고 추가 환불 발생하지 않음

---

## 4. 상태 전이 (State Diagrams)

### 4.1 BookingStatus

```mermaid
stateDiagram-v2
    [*] --> REQUESTED : placeOrder<br/>(선점 성공)

    REQUESTED --> CONFIRMED : confirm<br/>(결제 완료)
    REQUESTED --> EXPIRED : expire<br/>(선점 TTL 만료)
    REQUESTED --> CANCELLED : cancel<br/>(결제 전 취소)

    CONFIRMED --> CHECKED_IN : checkIn<br/>(자동 체크인 시간 / 호스트 처리)
    CONFIRMED --> CANCELLED : cancel<br/>(고객 취소 → 페널티 차감 환불)<br/>또는 hostCancel (100% 환불)

    CHECKED_IN --> CHECKED_OUT : checkOut

    CHECKED_OUT --> [*]
    CANCELLED --> [*]
    EXPIRED --> [*]

    note right of CONFIRMED
        체크인 이후 취소 불가 (불변조건)
    end note
    note right of CANCELLED
        취소된 예약 재확정 불가 (불변조건)
    end note
```

### 4.2 TransactionStatus

```mermaid
stateDiagram-v2
    [*] --> PENDING : initiate

    PENDING --> PAID : complete<br/>(PAYMENT detail 추가,<br/>TransactionCompletedEvent 발행)
    PENDING --> FAILED : fail<br/>(TransactionFailedEvent 발행<br/>— 만료 전 신규 Transaction 으로 재시도 가능)

    PAID --> CANCELLED : cancel<br/>(Booking EXPIRED 후 시스템 자동 취소,<br/>REFUND detail 추가,<br/>TransactionCancelledEvent 발행)
    PAID --> FULLY_CANCELLED : cancelWithPenalty<br/>(고객/호스트 취소,<br/>REFUND detail 1건 추가,<br/>TransactionFullyRefundedEvent 발행)

    PAID --> [*] : (단말 상태로 직행하지 않음)
    CANCELLED --> [*]
    FULLY_CANCELLED --> [*]
    FAILED --> [*]

    note right of FULLY_CANCELLED
        TransactionDetail 은 append-only.
        고객 취소는 REFUND row 1건만 추가되고
        페널티 잔액은 호스트 몫으로 남아
        추가 환불 대상 아님.
    end note
```

### 4.3 일정 상태 (Accommodation 일정 가시화)

```mermaid
stateDiagram-v2
    [*] --> 선택가능 : 호스트가 등록한 새 방의 일정

    선택가능 --> 선점됨 : SETNX 성공<br/>(Redis 키 + Shadow Key)<br/>또는 PreemptionFallback INSERT (Redis 장애 시)
    선점됨 --> 예약완료 : TransactionCompleted 수신 후<br/>ConfirmedBookingDate INSERT

    선점됨 --> 선택가능 : TTL 만료<br/>(SchedulePreemptionExpiredEvent)<br/>또는 명시적 release
    예약완료 --> 선택가능 : releaseConfirmed<br/>(TransactionFullyRefunded 또는 hostCancel 후)

    선택가능 --> 차단됨 : 호스트 BlockSchedule
    차단됨 --> 선택가능 : 호스트 UnblockSchedule

    note right of 선점됨
        예약 가능 여부 검사 시
        ConfirmedBookingDate +
        PreemptionFallback +
        Redis 키 모두 확인
    end note
```

---

## 5. 도메인 이벤트

### 5.1 이벤트 목록

```mermaid
classDiagram
    class DomainEvent {
        «Interface»
        +LocalDateTime occurredAt
        +String kafkaPartitionKey
    }

    class BookingDomainEvent {
        «Sealed interface»
    }
    class AccommodationDomainEvent {
        «Sealed interface»
    }
    class TransactionDomainEvent {
        «Sealed interface»
    }

    class BookingInitiatedEvent
    class BookingCancelledEvent
    class CheckInRecordedEvent
    class CheckOutRecordedEvent

    class SchedulePreemptedEvent
    class SchedulePreemptionExpiredEvent
    class BookingConfirmedEvent

    class TransactionCompletedEvent
    class TransactionFailedEvent
    class TransactionCancelledEvent
    class TransactionFullyRefundedEvent

    DomainEvent <|.. BookingDomainEvent
    DomainEvent <|.. AccommodationDomainEvent
    DomainEvent <|.. TransactionDomainEvent

    BookingDomainEvent <|.. BookingInitiatedEvent
    BookingDomainEvent <|.. BookingCancelledEvent
    BookingDomainEvent <|.. CheckInRecordedEvent
    BookingDomainEvent <|.. CheckOutRecordedEvent

    AccommodationDomainEvent <|.. SchedulePreemptedEvent
    AccommodationDomainEvent <|.. SchedulePreemptionExpiredEvent
    AccommodationDomainEvent <|.. BookingConfirmedEvent

    TransactionDomainEvent <|.. TransactionCompletedEvent
    TransactionDomainEvent <|.. TransactionFailedEvent
    TransactionDomainEvent <|.. TransactionCancelledEvent
    TransactionDomainEvent <|.. TransactionFullyRefundedEvent
```

**Kafka 토픽 ↔ 이벤트 매핑**

| 토픽 (`KafkaTopics`) | 이벤트 | 발행 컨텍스트 | 수신 컨텍스트 |
|----------------------|--------|---------------|---------------|
| `booking.events` | `BookingInitiatedEvent` | Booking | (관찰자만) |
| `booking.events` | `BookingCancelledEvent` | Booking | (관찰자만) |
| `accommodation.events` | `SchedulePreemptionExpiredEvent` | Accommodation | Booking |
| `accommodation.events` | `BookingConfirmedEvent` | Accommodation | Booking |
| `transaction.events` | `TransactionCompletedEvent` | Transaction | Accommodation |
| `transaction.events` | `TransactionFailedEvent` | Transaction | (관찰자만) |
| `transaction.events` | `TransactionCancelledEvent` | Transaction | Booking (확인용) |
| `transaction.events` | `TransactionFullyRefundedEvent` | Transaction | Booking |

모든 이벤트의 `kafkaPartitionKey = bookingOrderId` → 동일 예약의 이벤트 순서 보장.

### 5.2 정상 시나리오 — 예약 → 결제 → 확정

```mermaid
sequenceDiagram
    actor 고객
    participant BookingCtl as Booking REST
    participant BookingDS as BookingOrderDataService
    participant TxDS as TransactionDataService
    participant Redis
    participant Kafka
    participant AccConsumer as AccommodationTransactionEventConsumer
    participant AccDS as AccommodationConsumerDataService
    participant BookingConsumer as Booking AccommodationEventConsumer

    고객->>BookingCtl: POST /booking-orders (placeOrder)
    BookingCtl->>BookingDS: placeOrderTx
    BookingDS->>Redis: SETNX (선점 키)
    BookingDS-->>BookingCtl: BookingOrder(REQUESTED)
    BookingCtl-->>고객: orderId

    고객->>BookingCtl: POST /booking-orders/{id}/confirm
    BookingCtl->>BookingDS: confirmOrderTx
    BookingDS->>TxDS: initiate / completeAndPersist
    Note right of TxDS: 같은 DB tx (REQUIRED)
    TxDS-->>BookingDS: TransactionCompletedEvent
    BookingDS->>BookingDS: order.confirm()<br/>+ ConfirmedBookingDate INSERT
    BookingDS-->>BookingCtl: List<PendingEvent>
    BookingCtl->>Kafka: TransactionCompletedEvent (commit 후 발행)

    Kafka->>AccConsumer: TransactionCompletedEvent
    AccConsumer->>AccDS: handleTransactionCompletedTx
    AccDS->>AccDS: Booking 정상 → confirmPreemption (멱등)
    AccDS-->>AccConsumer: BookingConfirmedEvent (PendingEvent)
    AccConsumer->>Kafka: BookingConfirmedEvent

    Kafka->>BookingConsumer: BookingConfirmedEvent
    BookingConsumer->>BookingDS: confirmFromEventTx (멱등 — 이미 CONFIRMED)
    BookingDS-->>BookingConsumer: empty events
```

### 5.3 예외 시나리오 — TTL 만료 후 결제 도착

```mermaid
sequenceDiagram
    participant Redis
    participant Kafka
    participant AccListener as Redis Expiration Listener
    participant BookingConsumer as Booking AccommodationEventConsumer
    participant BookingDS as BookingOrderDataService
    participant TxDS as TransactionDataService
    participant AccConsumer as AccommodationTransactionEventConsumer
    participant AccDS as AccommodationConsumerDataService

    Note over Redis: 선점 키 TTL 만료
    Redis->>AccListener: keyspace notification
    AccListener->>Kafka: SchedulePreemptionExpiredEvent

    Kafka->>BookingConsumer: SchedulePreemptionExpiredEvent
    BookingConsumer->>BookingDS: expireFromEventTx
    BookingDS->>BookingDS: PESSIMISTIC_WRITE 락 + status=REQUESTED 검사
    BookingDS->>BookingDS: booking.expire() → EXPIRED

    Note over TxDS: (별도 흐름) 결제 완료 응답 도착
    TxDS->>TxDS: completeAndPersist → PAID
    TxDS->>Kafka: TransactionCompletedEvent (commit 후)

    Kafka->>AccConsumer: TransactionCompletedEvent
    AccConsumer->>AccDS: handleTransactionCompletedTx
    AccDS->>AccDS: Booking.status = EXPIRED 확인
    AccDS->>TxDS: cancelAndPersist (PAID → CANCELLED)
    AccDS-->>AccConsumer: TransactionCancelledEvent
    AccConsumer->>Kafka: TransactionCancelledEvent
```

### 5.4 고객 취소 시나리오 — 페널티 차감 환불

```mermaid
sequenceDiagram
    actor 고객
    participant BookingCtl as Booking REST
    participant BookingDS as BookingOrderDataService
    participant AccService as AccommodationService
    participant TxDS as TransactionDataService
    participant Kafka
    participant BookingConsumer as Booking TransactionEventConsumer

    고객->>BookingCtl: GET /booking-orders/{id}/cancellation-preview
    BookingCtl->>BookingDS: previewCancellation (read-only)
    BookingDS->>AccService: calculateRefundRatio(roomId, checkInDate)
    AccService-->>BookingDS: appliedRefundRatio
    BookingDS->>TxDS: findLatestPaidByBookingOrderId
    TxDS-->>BookingDS: paidAmount
    BookingDS-->>BookingCtl: refundAmount = paid × ratio
    BookingCtl-->>고객: 환불 미리보기

    고객->>BookingCtl: POST /booking-orders/{id}/cancel
    BookingCtl->>BookingDS: cancelOrderTx (CONFIRMED 분기)
    BookingDS->>BookingDS: PESSIMISTIC_WRITE 락
    BookingDS->>AccService: calculateRefundRatio
    BookingDS->>TxDS: cancelWithPenaltyAndPersist
    Note right of TxDS: PAID → FULLY_CANCELLED<br/>REFUND detail 1건 추가
    TxDS-->>BookingDS: TransactionFullyRefundedEvent
    BookingDS-->>BookingCtl: List<PendingEvent>
    BookingCtl->>Kafka: TransactionFullyRefundedEvent (commit 후)

    Kafka->>BookingConsumer: TransactionFullyRefundedEvent
    BookingConsumer->>BookingDS: cancelFromTransactionEventTx
    BookingDS->>BookingDS: 락 + 멱등 검사 (CANCELLED 면 skip)
    BookingDS->>BookingDS: booking.cancel() + releaseConfirmed
    BookingDS-->>BookingConsumer: BookingCancelledEvent
    BookingConsumer->>Kafka: BookingCancelledEvent
```

### 5.5 호스트 강제 취소 — 100% 환불

```mermaid
sequenceDiagram
    actor 호스트
    participant HostCtl as Host REST
    participant BookingDS as BookingOrderDataService
    participant TxDS as TransactionDataService
    participant Kafka

    호스트->>HostCtl: POST /host/booking-orders/{id}/cancel<br/>{reason}
    HostCtl->>BookingDS: hostCancelTx
    BookingDS->>BookingDS: 상태 검증 (REQUESTED/CONFIRMED)
    BookingDS->>TxDS: findLatestPaidByBookingOrderId
    BookingDS->>TxDS: cancelWithPenaltyAndPersist<br/>(refundAmount = paidAmount, 100%)
    TxDS-->>BookingDS: TransactionFullyRefundedEvent
    BookingDS-->>HostCtl: List<PendingEvent>
    HostCtl->>Kafka: TransactionFullyRefundedEvent
    Note right of Kafka: 이후 흐름은 5.4 와 동일<br/>(Booking CANCELLED 전이)
```

---

## 6. 데이터 무결성 보호 메커니즘 요약

| 메커니즘 | 대상 | 효과 |
|----------|------|------|
| `BookingOrder.{confirm/cancel/...}` 도메인 메서드 require | 상태 전이 | 불변조건 (체크인 후 취소 불가 등) 강제 |
| `Transaction.{cancel/cancelWithPenalty}` 도메인 메서드 require | 환불 처리 | PAID 외 상태에서 호출 차단 |
| `TransactionDetail` append-only | 환불 원장 | 이력 변조 차단 |
| Redis SETNX | 선점 race 차단 | 동일 키 동시 점유 불가 |
| `ConfirmedBookingDate` Unique `(room_id, reserved_date)` | 더블 부킹 차단 | 결제 완료 일정 race 차단 |
| `RoomSchedule` Unique `(room_id, blocked_date)` | 호스트 차단 일정 | 동일 날짜 중복 차단 차단 |
| `PreemptionFallback` Unique `(room_id, reserved_date)` | Redis 장애 시 race 차단 | DB 폴백 경로의 동시 선점 방지 |
| `BookingOrderJpaRepository.findByIdForUpdate` (PESSIMISTIC_WRITE, 5초 timeout) | Booking Aggregate 동시성 | 컨슈머·REST 동시 호출 직렬화 |
| `TransactionJpaRepository.findByIdForUpdate` (PESSIMISTIC_WRITE, 5초 timeout) | Transaction Aggregate 동시성 | 결제 상태 전이 직렬화 |
| Kafka partitionKey = `bookingOrderId` | 이벤트 순서 | 동일 예약 이벤트 순차 처리 보장 |
| 컨슈머 status self-check (CANCELLED/EXPIRED 면 skip) | 이벤트 중복 수신 | 멱등 처리 |
| `@Transactional` DataService + plain Orchestrator 분리 (Q3-A) | dual-write | DB commit 후 Kafka publish 보장 |

---

## 7. 참고

- 컨텍스트 간 호출 포트는 `application/port/in/*UseCase.kt` 인터페이스에 정의됨
- 영속성 어댑터는 `adapter/out/persistence/*JpaRepository.kt`, `adapter/out/redis/*Adapter.kt` 에 위치
- Kafka 컨슈머는 `adapter/in/kafka/*Consumer.kt`, REST 컨트롤러는 `adapter/in/web/*Controller.kt`
- 헥사고날 아키텍처를 따라 도메인 ↔ 인프라 의존성을 인터페이스로 차단

---

문서 갱신 이력
- 2026-04-27: Week3 완료 시점 (`AccommodationOperationPolicy`, `cancelWithPenalty`, `PreemptionFallback`, 자동 체크인, 비관적 락, Q3-A 일관 적용 모두 반영)
