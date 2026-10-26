CREATE TABLE inbox_message (
 id bigint PRIMARY KEY AUTO_INCREMENT, event_id bigint NOT NULL, recipient_type varchar(8) NOT NULL,
 recipient_id bigint NOT NULL, order_id bigint NOT NULL, store_id bigint NOT NULL,
 event_type varchar(48) NOT NULL, summary varchar(200) NOT NULL, created_at datetime NOT NULL,
 read_at datetime NULL,
 UNIQUE KEY uq_inbox_event(event_id,recipient_type,recipient_id),
 KEY ix_inbox_owner(recipient_type,recipient_id,id),
 KEY ix_inbox_unread(recipient_type,recipient_id,read_at,id)
);
