-- Week3 부분취소 도입 시 transaction_detail 테이블 컬럼 nullable 변경
-- ddl-auto=update 환경(PostgreSQL)에서 Hibernate가 NOT NULL 제약을 자동 제거하지 않으므로
-- 아래 DDL을 한 번 수동으로 실행해야 한다.
--
-- 배경:
--   PARTIAL_REFUND 타입 TransactionDetail은 ledgerInfo(결제 원장)가 없어 관련 컬럼이 null.
--   LedgerInfo 원본에는 nullable=false 이지만, TransactionDetail에서 @AttributeOverrides로
--   nullable=true 로 재정의했다. DB 테이블은 이 변경을 자동 적용하지 않으므로 수동 적용 필요.

ALTER TABLE transaction_detail ALTER COLUMN approval_number   DROP NOT NULL;
ALTER TABLE transaction_detail ALTER COLUMN pg_transaction_id DROP NOT NULL;
ALTER TABLE transaction_detail ALTER COLUMN pg_name           DROP NOT NULL;
ALTER TABLE transaction_detail ALTER COLUMN paid_amount       DROP NOT NULL;
ALTER TABLE transaction_detail ALTER COLUMN paid_currency     DROP NOT NULL;

-- Hibernate 6.x 는 @Enumerated(EnumType.STRING) 컬럼에 CHECK 제약을 자동 생성한다.
-- TransactionDetailType 에 PARTIAL_REFUND 추가 후 기존 제약이 해당 값을 막으므로
-- 제약을 드롭하고 새 값을 포함한 제약으로 재생성한다.
ALTER TABLE transaction_detail DROP CONSTRAINT IF EXISTS transaction_detail_type_check;
ALTER TABLE transaction_detail ADD CONSTRAINT transaction_detail_type_check
  CHECK (type IN ('PAYMENT', 'REFUND', 'PARTIAL_REFUND'));
