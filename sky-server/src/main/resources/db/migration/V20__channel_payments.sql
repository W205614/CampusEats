CREATE TABLE merchant_payment_config (
 store_id bigint PRIMARY KEY,sub_mchid varchar(32) NOT NULL,enabled boolean NOT NULL DEFAULT false,
 version bigint NOT NULL DEFAULT 0,updated_at datetime NOT NULL
);
CREATE TABLE payment_intent (
 id varchar(32) PRIMARY KEY,checkout_id bigint NOT NULL,user_id bigint NOT NULL,request_key varchar(64) NOT NULL,
 channel varchar(16) NOT NULL,state varchar(16) NOT NULL,app_id varchar(32) NOT NULL,total_cents bigint NOT NULL,
 expires_at datetime NOT NULL,created_at datetime NOT NULL,updated_at datetime NOT NULL,
 action_json mediumtext NULL,last_error varchar(64) NULL,close_requested boolean NOT NULL DEFAULT false,
 payer_openid varchar(128) NULL,client_ip varchar(45) NOT NULL,notify_base varchar(512) NOT NULL DEFAULT '',submitted boolean NOT NULL DEFAULT false,attempts int NOT NULL DEFAULT 0,
 lease_token varchar(36) NULL,lease_until datetime NULL,next_check_at datetime NOT NULL,
 UNIQUE KEY uq_payment_request(user_id,request_key),UNIQUE KEY uq_payment_checkout(checkout_id),
 KEY ix_payment_due(state,next_check_at),CHECK(total_cents>=0)
);
CREATE TABLE payment_channel_order (
 intent_id varchar(32) NOT NULL,order_id bigint NOT NULL,store_id bigint NOT NULL,
 out_trade_no varchar(32) NOT NULL,sub_mchid varchar(32) NOT NULL,amount_cents bigint NOT NULL,
 transaction_id varchar(64) NULL,success_time datetime NULL,PRIMARY KEY(intent_id,order_id),UNIQUE KEY uq_channel_trade(out_trade_no),CHECK(amount_cents>=0)
);
CREATE TABLE payment_callback (
 event_id varchar(64) PRIMARY KEY,kind varchar(16) NOT NULL,body_sha256 char(64) NOT NULL,created_at datetime NOT NULL
);
CREATE TABLE channel_refund (
 request_id bigint PRIMARY KEY,intent_id varchar(32) NOT NULL,sub_mchid varchar(32) NOT NULL,
 out_trade_no varchar(32) NOT NULL,out_refund_no varchar(32) NOT NULL UNIQUE,
 amount_cents bigint NOT NULL,total_cents bigint NOT NULL,state varchar(16) NOT NULL,notify_base varchar(512) NOT NULL DEFAULT '',
 provider_id varchar(64) NULL,submitted boolean NOT NULL DEFAULT false,accepted_at datetime NULL,succeeded_at datetime NULL,updated_at datetime NOT NULL
);
CREATE TABLE channel_reconciliation (
 id bigint PRIMARY KEY AUTO_INCREMENT,store_id bigint NOT NULL,business_date date NOT NULL,
 state varchar(16) NOT NULL,trade_count int NOT NULL DEFAULT 0,difference_count int NOT NULL DEFAULT 0,
 fee_cents bigint NOT NULL DEFAULT 0,bill_sha256 char(64) NULL,raw_csv mediumtext NULL,created_at datetime NOT NULL,
 UNIQUE KEY uq_channel_reconciliation(store_id,business_date)
);
CREATE TABLE channel_difference (
 id bigint PRIMARY KEY AUTO_INCREMENT,reconciliation_id bigint NOT NULL,store_id bigint NOT NULL,
 reference varchar(64) NOT NULL,kind varchar(32) NOT NULL,expected_cents bigint NULL,actual_cents bigint NULL,
 state varchar(16) NOT NULL DEFAULT 'OPEN',resolution varchar(200) NULL,
 UNIQUE KEY uq_channel_difference(reconciliation_id,reference,kind)
);
CREATE TABLE joint_coupon_store (
 coupon_id bigint NOT NULL,store_id bigint NOT NULL,PRIMARY KEY(coupon_id,store_id)
);
ALTER TABLE checkout ADD payment_provider varchar(16) NOT NULL DEFAULT 'MOCK';
CREATE TABLE integration_acceptance (
 provider varchar(32) NOT NULL,source_revision varchar(100) NOT NULL,evidence_ref varchar(500) NOT NULL,
 actor_id bigint NOT NULL,accepted_at datetime NOT NULL,PRIMARY KEY(provider,source_revision)
);
