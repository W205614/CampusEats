-- Missing historical event times must not be presented as actual payment/refund dates.
ALTER TABLE checkout ADD paid_time_source varchar(24) NOT NULL DEFAULT 'ACTUAL';
ALTER TABLE refund_request ADD success_time_source varchar(24) NOT NULL DEFAULT 'ACTUAL';
UPDATE checkout c JOIN orders o ON o.checkout_id=c.id SET c.paid_at=NULL,c.paid_time_source='LEGACY_UNKNOWN' WHERE c.quote_json='{}' AND o.pay_status<>0 AND o.checkout_time IS NULL;
UPDATE refund_request r JOIN refund_task legacy ON legacy.id=r.id AND legacy.order_id=r.order_id SET r.succeeded_at=NULL,r.success_time_source='LEGACY_UNKNOWN' WHERE r.state='SUCCEEDED';
UPDATE orders o JOIN checkout c ON c.id=o.checkout_id SET o.completed_at=NULL WHERE c.quote_json='{}' AND o.status=5 AND o.delivery_time IS NULL;
