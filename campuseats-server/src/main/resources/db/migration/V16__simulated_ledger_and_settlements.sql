-- A truthful opening snapshot: these are migration balances, not reconstructed historic cashflows.
CREATE TABLE finance_cutover(id int PRIMARY KEY,captured_at datetime(6) NOT NULL);
INSERT INTO finance_cutover VALUES(1,CURRENT_TIMESTAMP(6));
CREATE TABLE finance_opening(
 order_id bigint PRIMARY KEY,store_id bigint NOT NULL,paid_cents bigint NOT NULL,
 refunded_cents bigint NOT NULL,captured_at datetime(6) NOT NULL,
 KEY ix_finance_opening_store(store_id,order_id),
 CHECK(paid_cents>=0 AND refunded_cents>=0 AND refunded_cents<=paid_cents)
);
INSERT INTO finance_opening(order_id,store_id,paid_cents,refunded_cents,captured_at)
 SELECT o.id,o.store_id,o.paid_cents,o.refunded_cents,c.captured_at FROM orders o CROSS JOIN finance_cutover c;
CREATE TABLE finance_ledger(
 id bigint PRIMARY KEY AUTO_INCREMENT,source_type varchar(12) NOT NULL,source_id bigint NOT NULL,
 order_id bigint NOT NULL,store_id bigint NOT NULL,amount_cents bigint NOT NULL,occurred_at datetime(6) NOT NULL,
 UNIQUE KEY uq_finance_source(source_type,source_id),
 KEY ix_finance_ledger_store(store_id,id),KEY ix_finance_ledger_date(store_id,occurred_at,id),
 KEY ix_finance_ledger_order(order_id,id),
 CHECK((source_type='PAYMENT' AND amount_cents>=0) OR (source_type='REFUND' AND amount_cents<=0))
);
CREATE TABLE finance_settlement(
 id bigint PRIMARY KEY AUTO_INCREMENT,store_id bigint NOT NULL,business_date date NOT NULL,
 period_start datetime(6) NOT NULL,period_end datetime(6) NOT NULL,paid_cents bigint NOT NULL,
 refunded_cents bigint NOT NULL,net_cents bigint NOT NULL,entry_count bigint NOT NULL,
 created_at datetime(6) NOT NULL,UNIQUE KEY uq_finance_settlement(store_id,business_date),
 KEY ix_finance_settlement_store(store_id,id),
 CHECK(paid_cents>=0 AND refunded_cents>=0 AND net_cents=paid_cents-refunded_cents)
);
CREATE TABLE finance_settlement_entry(
 settlement_id bigint NOT NULL,ledger_id bigint NOT NULL,PRIMARY KEY(settlement_id,ledger_id),
 UNIQUE KEY uq_finance_settled_entry(ledger_id)
);
