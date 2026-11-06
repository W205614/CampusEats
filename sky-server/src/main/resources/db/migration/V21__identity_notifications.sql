CREATE TABLE external_identity (
  id bigint PRIMARY KEY AUTO_INCREMENT,
  user_id bigint NOT NULL,
  provider varchar(16) NOT NULL,
  app_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  subject varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  created_at datetime NOT NULL,
  UNIQUE KEY uq_external_subject(provider,app_id,subject),
  UNIQUE KEY uq_external_user_app(user_id,provider,app_id)
);
CREATE TABLE verified_phone (
  phone varchar(11) PRIMARY KEY,
  user_id bigint NOT NULL UNIQUE,
  verified_at datetime NOT NULL
);
CREATE TABLE privacy_consent (
  id bigint PRIMARY KEY AUTO_INCREMENT,
  user_id bigint NOT NULL,
  policy_version varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  accepted_at datetime NOT NULL,
  UNIQUE KEY uq_privacy_consent(user_id,policy_version)
);
CREATE TABLE account_closure (
  id bigint PRIMARY KEY AUTO_INCREMENT,
  user_id bigint NOT NULL UNIQUE,
  state varchar(16) NOT NULL,
  requested_at datetime NOT NULL,
  completed_at datetime NULL
);
CREATE TABLE notification_subscription (
  user_id bigint NOT NULL,
  app_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  template_id varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  credits int NOT NULL DEFAULT 0,
  updated_at datetime NOT NULL,
  PRIMARY KEY(user_id,app_id,template_id),
  CHECK(credits>=0)
);
CREATE TABLE notification_delivery (
  id bigint PRIMARY KEY AUTO_INCREMENT,
  event_id bigint NOT NULL,
  user_id bigint NOT NULL,
  order_id bigint NOT NULL,
  channel varchar(16) NOT NULL,
  template_id varchar(128) NOT NULL,
  event_type varchar(64) NOT NULL DEFAULT '',
  field_mapping mediumtext NOT NULL DEFAULT ('{}'),
  state varchar(16) NOT NULL DEFAULT 'REQUESTED',
  summary varchar(200) NOT NULL,
  channel_reference varchar(128) NULL,
  lease_token varchar(36) NULL,
  lease_until datetime NULL,
  next_attempt_at datetime NOT NULL,
  attempts int NOT NULL DEFAULT 0,
  last_error varchar(80) NULL,
  created_at datetime NOT NULL,
  updated_at datetime NOT NULL,
  UNIQUE KEY uq_notification_event(event_id,user_id,channel),
  KEY ix_notification_due(state,next_attempt_at)
);
