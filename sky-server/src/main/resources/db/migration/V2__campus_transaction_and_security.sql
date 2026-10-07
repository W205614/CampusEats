-- Additive migration. Unique DDL intentionally fails on duplicates; see scripts/migration-preflight.ps1.
ALTER TABLE employee MODIFY password varchar(255) NOT NULL, ADD role varchar(16) NOT NULL DEFAULT 'OPERATOR', ADD auth_version bigint NOT NULL DEFAULT 0, ADD must_change_password boolean NOT NULL DEFAULT true;
UPDATE employee SET role='ADMIN' WHERE id=1;
ALTER TABLE user ADD status int NOT NULL DEFAULT 1, ADD auth_version bigint NOT NULL DEFAULT 0, ADD default_address_id bigint NULL, ADD UNIQUE KEY uq_user_openid(openid), ADD KEY ix_user_create(create_time);
ALTER TABLE dish ADD default_quota int NOT NULL DEFAULT 100, ADD KEY ix_dish_category_status(category_id,status);
ALTER TABLE setmeal ADD KEY ix_setmeal_category_status(category_id,status);
ALTER TABLE dish_flavor ADD KEY ix_flavor_dish(dish_id);
ALTER TABLE setmeal_dish ADD KEY ix_component_setmeal(setmeal_id), ADD KEY ix_component_dish(dish_id);
ALTER TABLE address_book ADD building_id bigint NULL, ADD room varchar(32) NULL, ADD KEY ix_address_user(user_id);
ALTER TABLE orders ADD request_id varchar(36) NULL, ADD request_hash char(64) NULL, ADD version bigint NOT NULL DEFAULT 0,
 ADD business_date date NULL, ADD snapshot_source varchar(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN',
 ADD building_name varchar(80) NULL, ADD room varchar(32) NULL,
 ADD delivery_fee decimal(10,2) NOT NULL DEFAULT 0, ADD packaging_fee decimal(10,2) NOT NULL DEFAULT 0,
 ADD pay_expires_at datetime NULL, ADD delivery_started_at datetime NULL, ADD courier_id bigint NULL, ADD delivery_overdue boolean NOT NULL DEFAULT false,
 ADD UNIQUE KEY uq_order_number(number), ADD UNIQUE KEY uq_order_request(user_id,request_id),
 ADD KEY ix_order_user_time(user_id,order_time,id), ADD KEY ix_order_status_time(status,order_time,id), ADD KEY ix_order_courier(courier_id,status),
 ADD KEY ix_order_expiry(status,pay_expires_at), ADD KEY ix_order_business_date(business_date);
ALTER TABLE order_detail MODIFY dish_flavor varchar(1000) NULL, ADD KEY ix_detail_order(order_id);
-- A current address is only a marked fallback, never represented as an original snapshot.
UPDATE orders o JOIN address_book a ON a.id=o.address_book_id AND a.user_id=o.user_id
 SET o.address=CONCAT_WS('',a.province_name,a.city_name,a.district_name,a.detail),o.snapshot_source='LEGACY_BACKFILL'
 WHERE o.address IS NULL OR o.address='';
UPDATE orders SET snapshot_source='LEGACY_EXISTING' WHERE address IS NOT NULL AND address<>'' AND snapshot_source='LEGACY_UNKNOWN';
UPDATE orders SET pay_expires_at=DATE_ADD(order_time,INTERVAL 15 MINUTE) WHERE status=1 AND pay_expires_at IS NULL;
CREATE TABLE campus_building(id bigint PRIMARY KEY AUTO_INCREMENT,name varchar(80) NOT NULL UNIQUE,enabled boolean NOT NULL DEFAULT true);
CREATE TABLE shop_settings(id int PRIMARY KEY,open boolean NOT NULL DEFAULT false,delivery_fee decimal(10,2) NOT NULL,packaging_fee decimal(10,2) NOT NULL,hours varchar(500) NOT NULL,phone varchar(20) NOT NULL DEFAULT '',version bigint NOT NULL DEFAULT 0);
INSERT INTO shop_settings(id,open,delivery_fee,packaging_fee,hours) VALUES(1,false,6.00,1.00,'["00:00-24:00"]');
CREATE TABLE cart_state(user_id bigint PRIMARY KEY,version bigint NOT NULL DEFAULT 0);
CREATE TABLE cart_item(id bigint PRIMARY KEY AUTO_INCREMENT,user_id bigint NOT NULL,item_type varchar(8) NOT NULL,item_id bigint NOT NULL,flavors varchar(1000) NOT NULL,flavor_hash char(64) NOT NULL,quantity int NOT NULL,CHECK(quantity BETWEEN 1 AND 50),UNIQUE KEY uq_cart_item(user_id,item_type,item_id,flavor_hash));
CREATE TABLE daily_quota(business_date date NOT NULL,dish_id bigint NOT NULL,total int NOT NULL,reserved int NOT NULL DEFAULT 0,consumed int NOT NULL DEFAULT 0,PRIMARY KEY(business_date,dish_id),CHECK(total>=reserved+consumed),CHECK(reserved>=0),CHECK(consumed>=0));
CREATE TABLE order_reservation(order_id bigint NOT NULL,dish_id bigint NOT NULL,business_date date NOT NULL,quantity int NOT NULL,state varchar(12) NOT NULL,PRIMARY KEY(order_id,dish_id),KEY ix_reservation_date(business_date,dish_id));
CREATE TABLE auth_session(jti varchar(36) PRIMARY KEY,subject_type varchar(8) NOT NULL,subject_id bigint NOT NULL,auth_version bigint NOT NULL,expires_at datetime NOT NULL,revoked boolean NOT NULL DEFAULT false,KEY ix_session_subject(subject_type,subject_id));
CREATE TABLE refund_task(id bigint PRIMARY KEY AUTO_INCREMENT,order_id bigint NOT NULL UNIQUE,state varchar(16) NOT NULL DEFAULT 'REQUESTED',attempts int NOT NULL DEFAULT 0,next_attempt_at datetime NOT NULL,lease_until datetime NULL,lease_token varchar(36) NULL,last_error varchar(80) NULL,created_at datetime NOT NULL,KEY ix_refund_due(state,next_attempt_at));
CREATE TABLE payment_attempt(id bigint PRIMARY KEY AUTO_INCREMENT,order_id bigint NOT NULL UNIQUE,gateway_ref varchar(80) NOT NULL UNIQUE,created_at datetime NOT NULL);
CREATE TABLE outbox_event(id bigint PRIMARY KEY AUTO_INCREMENT,event_type varchar(32) NOT NULL,aggregate_id bigint NOT NULL,payload text NOT NULL,state varchar(16) NOT NULL DEFAULT 'REQUESTED',attempts int NOT NULL DEFAULT 0,next_attempt_at datetime NOT NULL,lease_until datetime NULL,lease_token varchar(36) NULL,last_error varchar(80) NULL,created_at datetime NOT NULL,KEY ix_outbox_due(state,next_attempt_at));
CREATE TABLE audit_log(id bigint PRIMARY KEY AUTO_INCREMENT,actor_type varchar(16) NOT NULL,actor_id bigint NOT NULL,action varchar(48) NOT NULL,target_id bigint NOT NULL,detail varchar(200) NOT NULL,created_at datetime NOT NULL,KEY ix_audit_time(created_at,id));
