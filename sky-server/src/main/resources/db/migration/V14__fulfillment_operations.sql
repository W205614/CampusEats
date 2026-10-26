ALTER TABLE employee ADD accepting_tasks boolean NOT NULL DEFAULT true;
CREATE TABLE service_policy (
 store_id bigint PRIMARY KEY, accept_minutes int NOT NULL DEFAULT 10,
 prepare_minutes int NOT NULL DEFAULT 30, dispatch_minutes int NOT NULL DEFAULT 15,
 pickup_minutes int NOT NULL DEFAULT 15, delivery_minutes int NOT NULL DEFAULT 60,
 self_pickup_minutes int NOT NULL DEFAULT 120, version bigint NOT NULL DEFAULT 0,
 CHECK(accept_minutes BETWEEN 1 AND 1440 AND prepare_minutes BETWEEN 1 AND 1440
 AND dispatch_minutes BETWEEN 1 AND 1440 AND pickup_minutes BETWEEN 1 AND 1440
 AND delivery_minutes BETWEEN 1 AND 1440 AND self_pickup_minutes BETWEEN 1 AND 1440)
);
CREATE TABLE order_stage (
 order_id bigint PRIMARY KEY, stage varchar(24) NOT NULL, stage_seq bigint NOT NULL DEFAULT 0,
 started_at datetime NOT NULL, deadline_at datetime NULL, migrated boolean NOT NULL DEFAULT false,
 KEY ix_order_stage_due(deadline_at,order_id)
);
CREATE TABLE order_exception (
 id bigint PRIMARY KEY AUTO_INCREMENT, order_id bigint NOT NULL, store_id bigint NOT NULL,
 campus_id bigint NOT NULL, kind varchar(32) NOT NULL, stage_seq bigint NOT NULL,
 stage varchar(24) NOT NULL, deadline_at datetime NULL, migrated boolean NOT NULL DEFAULT false,
 cause_key varchar(80) NOT NULL DEFAULT '',
 state varchar(16) NOT NULL DEFAULT 'OPEN', reason varchar(200) NULL, version bigint NOT NULL DEFAULT 0,
 created_at datetime NOT NULL, acknowledged_at datetime NULL, resolved_at datetime NULL,
 UNIQUE KEY uq_order_exception(order_id,kind,stage_seq,cause_key),
 KEY ix_exception_store(store_id,state,id), KEY ix_exception_state(state,id)
);
INSERT INTO service_policy(store_id) SELECT id FROM store;
INSERT INTO order_stage(order_id,stage,started_at,deadline_at,migrated)
 SELECT o.id,CASE WHEN o.status=2 THEN 'WAIT_ACCEPT' WHEN o.status=4 THEN 'DELIVERING'
 WHEN NOT o.ready THEN 'PREPARING' WHEN o.fulfillment='PICKUP' THEN 'SELF_PICKUP'
 WHEN t.state='ASSIGNED' THEN 'WAIT_PICKUP' ELSE 'WAIT_DISPATCH' END,
 CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL CASE WHEN o.status=2 THEN 10
 WHEN o.status=4 THEN 60 WHEN NOT o.ready THEN 30 WHEN o.fulfillment='PICKUP' THEN 120
 ELSE 15 END MINUTE),true FROM orders o LEFT JOIN delivery_task t ON t.order_id=o.id
 WHERE o.status BETWEEN 2 AND 4;
