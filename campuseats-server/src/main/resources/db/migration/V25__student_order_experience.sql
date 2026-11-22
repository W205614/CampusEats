ALTER TABLE store ADD pickup_point VARCHAR(160) NULL;
ALTER TABLE orders ADD pickup_point VARCHAR(160) NULL;
ALTER TABLE inbox_message ADD refund_id BIGINT NULL;
