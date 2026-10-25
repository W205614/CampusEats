-- Local simulation of a gateway decision. No foreign keys: the receipt commits separately
-- and must survive a rollback or process loss before the business refund is applied.
CREATE TABLE mock_refund_receipt (
  business_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
  payment_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  amount_cents bigint NOT NULL,
  currency char(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reference varchar(160) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK(amount_cents >= 0)
);
