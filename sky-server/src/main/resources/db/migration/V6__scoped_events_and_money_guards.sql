ALTER TABLE outbox_event ADD store_id bigint NULL,ADD campus_id bigint NULL,ADD KEY ix_outbox_store(store_id,state,id);
UPDATE outbox_event e JOIN orders o ON o.id=e.aggregate_id SET e.store_id=o.store_id,e.campus_id=o.campus_id;
UPDATE outbox_event SET store_id=1,campus_id=1 WHERE event_type='CACHE_INVALIDATE' AND store_id IS NULL;
UPDATE audit_log SET store_id=1,campus_id=1 WHERE store_id IS NULL AND actor_type<>'SYSTEM';
ALTER TABLE orders ADD CONSTRAINT ck_order_refunds CHECK(paid_cents>=0 AND refunded_cents>=0 AND reserved_refund_cents>=0 AND refunded_cents+reserved_refund_cents<=paid_cents);
ALTER TABLE order_detail ADD CONSTRAINT ck_detail_refunds CHECK(refunded_quantity>=0 AND reserved_quantity>=0 AND refunded_quantity+reserved_quantity<=number);
