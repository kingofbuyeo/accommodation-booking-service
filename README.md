# accommodation-booking-service

숙박 예약 시스템의 백엔드 서비스. DDD Gym Week1/Week2 실습을 통해 설계하고 구현한 결과물.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Kotlin 1.9.23 |
| Runtime | Java 21 |
| Framework | Spring Boot 3.2.5 |
| ORM | Spring Data JPA (Hibernate) |
| DB (기본) | H2 in-memory (MySQL mode) |
| DB (운영) | PostgreSQL (드라이버 포함) |
| Cache / 선점 | Redis (Spring Data Redis) |
| 메시지 브로커 | Kafka (Spring Kafka) |
| 빌드 도구 | Gradle (Kotlin DSL) |
| 테스트 | JUnit 5, Testcontainers (Kafka) |

---

## 아키텍처: 헥사고날(Ports & Adapters)

```
com.yongchul.booking
├── accommodation/
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── kafka/          # AccommodationTransactionEventConsumer
│   │   │   └── web/            # AccommodationController + DTO
│   │   └── out/
│   │       ├── persistence/    # JPA Repository Adapters
│   │       └── redis/          # Redis Preemption Adapter
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/             # UseCase 인터페이스
│   │   │   └── out/            # Port 인터페이스
│   │   └── service/            # AccommodationService, SchedulePreemptionService
│   └── domain/                 # Accommodation, Room, RoomSchedule, ...
├── booking/
│   ├── adapter/in/...
│   ├── application/...
│   └── domain/                 # BookingOrder, BookingOrderLineItem
├── transaction/
│   ├── adapter/out/...
│   ├── application/...
│   └── domain/                 # Transaction, TransactionDetail
└── common/                     # DateRange, Money, DomainEvent, Kafka/Redis/Web 설정
```

---

## 바운디드 컨텍스트

### 1. Accommodation (숙소)

숙소와 객실의 등록, 조회, 일정 관리를 담당한다.

**도메인 엔티티**

| 엔티티 | 설명 |
|--------|------|
| `Accommodation` | 숙소 Aggregate Root. 이름, 주소, 호스트명, PreemptionPolicy 보유 |
| `Room` | 객실. 숙박 가격(Money), 수용 인원, PreemptionPolicy(nullable) |
| `RoomSchedule` | 호스트가 직접 차단한 날짜 레코드. type: BLOCKED / MAINTENANCE / OWNER_USE |
| `ConfirmedBookingDate` | 결제 확정된 예약 날짜 스냅샷 (Booking 컨텍스트와 ID 참조) |

**값 객체**

| 값 객체 | 설명 |
|---------|------|
| `PreemptionPolicy` | 선점 TTL 정책. 리드타임(체크인까지 남은 일수)에 따라 TTL 차등 결정 |
| `SchedulePreemptionKey` | Redis 선점 키 구조체 (roomId + date) |
| `Money` | 금액 + 통화 (BigDecimal, KRW 기본) |
| `DateRange` | checkIn ~ checkOut 구간, nights 계산 포함 |

**PreemptionPolicy TTL 계산 규칙**

```
리드타임 ≤ shortLeadTimeDays(기본 3일)  → shortLeadTimeTtlMinutes(기본 20분)
리드타임 ≥ longLeadTimeDays(기본 30일)  → longLeadTimeTtlMinutes(기본 120분)
그 외                                    → defaultTtlMinutes(기본 60분)
```

리드타임이 짧을수록 TTL을 단축해 공실 기회 손실을 최소화하고,  
리드타임이 길수록 TTL을 연장해 고객 결제 여유 시간을 확보한다.

**PreemptionPolicy nullable 설계**

`ddl-auto: update` 환경에서 컬럼이 이후 추가된 경우 기존 DB 행은 null로 로드된다.  
Hibernate가 `@Embedded` 필드를 null로 설정하므로 Kotlin 타입을 `PreemptionPolicy?`로 선언하고,  
사용 시점에 `?: PreemptionPolicy()`로 기본값을 제공한다.

---

### 2. Booking (예약)

예약 주문 생성부터 체크아웃까지의 상태 흐름을 관리한다.

**도메인 엔티티**

| 엔티티 | 설명 |
|--------|------|
| `BookingOrder` | 예약 Aggregate Root. 상태 전이 메서드와 불변조건 보유 |
| `BookingOrderLineItem` | 예약 라인 아이템. 숙소/객실 스냅샷, 체크인/아웃, 금액 |

**값 객체**

| 값 객체 | 설명 |
|---------|------|
| `GuestInfo` | 예약자 이름 |
| `AccommodationSnapshot` | 예약 시점의 숙소 이름 스냅샷 |
| `RoomSnapshot` | 예약 시점의 객실 이름/가격 스냅샷 |

**상태 흐름 및 불변조건**

```
REQUESTED → CONFIRMED  (결제 확정)
          → EXPIRED    (선점 TTL 만료 시 Redis keyspace event → expire() 호출)
          → CANCELLED  (취소)

CONFIRMED → CHECKED_IN  (체크인)
          → CANCELLED   (취소)

CHECKED_IN → CHECKED_OUT (체크아웃)
```

불변조건:
- 취소된 예약은 재확정할 수 없다
- 체크인 이후 취소할 수 없다
- 체크인 없이 체크아웃할 수 없다
- 만료된 예약은 확정할 수 없다

`expiresAt`: REQUESTED 상태에서 선점 만료 시각. FE가 카운트다운 표시에 사용.

---

### 3. Transaction (결제)

결제 처리 및 환불을 담당한다. Booking 이벤트를 구독하여 동작한다.

**도메인 엔티티**

| 엔티티 | 설명 |
|--------|------|
| `Transaction` | 결제 Aggregate Root |
| `TransactionDetail` | 결제/환불 내역 (append-only) |

**상태 흐름**

```
PENDING → PAID → PARTIAL_CANCELLED → FULLY_CANCELLED
        → FAILED
        → CANCELLED
```

불변조건:
- `TransactionDetail`은 append-only (수정 불가)
- 잔여 결제 금액이 0이 되면 FULLY_CANCELLED로 전이

---

## 도메인 이벤트 & Kafka

**토픽 및 파티션 키**

모든 이벤트의 `kafkaPartitionKey = bookingOrderId` — 동일 예약의 이벤트 순서를 보장한다.

| 이벤트 | 발행 컨텍스트 | 구독 컨텍스트 |
|--------|--------------|--------------|
| `BookingInitiatedEvent` | Booking | Transaction, Accommodation |
| `BookingCancelledEvent` | Booking | Transaction, Accommodation |
| `CheckInRecordedEvent` | Booking | Transaction |
| `CheckOutRecordedEvent` | Booking | Transaction |
| `TransactionCompletedEvent` | Transaction | Accommodation (ConfirmedBookingDate 생성) |
| `TransactionCancelledEvent` | Transaction | Accommodation (ConfirmedBookingDate 삭제) |

---

## REST API

### Accommodation

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/v1/accommodations` | 숙소 전체 목록 조회 |
| `GET` | `/api/v1/accommodations/{id}` | 숙소 단건 조회 (객실, 일정 포함) |
| `POST` | `/api/v1/accommodations` | 숙소 등록 |
| `POST` | `/api/v1/accommodations/{id}/rooms` | 객실 추가 |
| `POST` | `/api/v1/accommodations/{id}/rooms/{roomId}/schedules/block` | 단일 날짜 차단 |
| `POST` | `/api/v1/accommodations/{id}/rooms/{roomId}/schedules/block/bulk` | 복수 날짜 일괄 차단 |
| `DELETE` | `/api/v1/accommodations/{id}/rooms/{roomId}/schedules/block/{date}` | 날짜 차단 해제 |

**일괄 차단 요청 본문**

```json
{
  "dates": ["2025-01-10", "2025-01-11", "2025-01-12"],
  "type": "MAINTENANCE",
  "reason": "정기 청소"
}
```

`RoomScheduleType`: `BLOCKED` | `MAINTENANCE` | `OWNER_USE`

### Booking

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/v1/booking-orders` | 예약 생성 (선점 + expiresAt 설정) |
| `GET` | `/api/v1/booking-orders/{orderId}` | 예약 조회 |
| `POST` | `/api/v1/booking-orders/{orderId}/confirm` | 결제 확정 |
| `POST` | `/api/v1/booking-orders/{orderId}/cancel` | 예약 취소 |
| `POST` | `/api/v1/booking-orders/{orderId}/check-in` | 체크인 |
| `POST` | `/api/v1/booking-orders/{orderId}/check-out` | 체크아웃 |

---

## Redis 선점 메커니즘

1. 예약 생성 시 `SchedulePreemptionKey(roomId, date)`를 Redis에 TTL과 함께 저장
2. TTL 만료 시 keyspace event 수신 → `BookingOrder.expire()` 호출 → EXPIRED 상태 전이
3. TTL은 `PreemptionPolicy.calculateTtl(checkInDate)` 결과로 결정
4. `BookingOrder.expiresAt` 필드에 만료 시각 저장 → FE 카운트다운에 활용

---

## 엔티티 간 참조 규칙

컨텍스트 간 엔티티는 `@ManyToOne` / `@OneToMany` JPA 연관 대신 **ID 참조**만 사용한다.

```kotlin
// BookingOrderLineItem은 숙소를 ID로만 참조
val accommodationId: Long
val roomId: Long
// — AccommodationSnapshot으로 이름 등 비정규화 정보 보관
val accommodationSnapshot: AccommodationSnapshot
```

---

## 실행 방법

### 사전 요구 사항

- JDK 21
- Docker (Redis + Kafka 실행용)

### Redis & Kafka 시작

```bash
docker run -d --name redis -p 6379:6379 redis
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  apache/kafka
```

### 서버 실행

```bash
./gradlew bootRun
```

기본 설정(application.yml)은 H2 in-memory DB + localhost Redis/Kafka를 사용한다.

### H2 콘솔

브라우저에서 `http://localhost:8080/h2-console` 접속  
JDBC URL: `jdbc:h2:mem:accommodation`

### 테스트

```bash
./gradlew test
```

---

## 유비쿼터스 언어

| 용어 (한국어) | 용어 (영어) | 설명 |
|--------------|------------|------|
| 숙소 | Accommodation | 예약 가능한 숙박 시설 |
| 객실 | Room | 숙소 내 예약 단위 |
| 예약 주문 | BookingOrder | 예약 요청부터 체크아웃까지의 라이프사이클 |
| 예약 라인 아이템 | BookingOrderLineItem | 예약 주문 내 숙박 상세 (날짜, 금액, 스냅샷) |
| 결제 | Transaction | 예약에 대한 금전 거래 |
| 결제 내역 | TransactionDetail | 결제/환불 이력 (append-only) |
| 선점 | Preemption | 예약 요청 시 일정을 임시 잠금하는 메커니즘 |
| 선점 정책 | PreemptionPolicy | 리드타임에 따른 선점 TTL 결정 규칙 |
| 리드타임 | Lead Time | 예약 시점으로부터 체크인까지 남은 일수 |
| 차단 일정 | RoomSchedule | 호스트가 수동으로 예약 불가 처리한 날짜 |
| 확정 예약 날짜 | ConfirmedBookingDate | 결제 확정 후 숙소 컨텍스트에 기록된 예약 날짜 |
