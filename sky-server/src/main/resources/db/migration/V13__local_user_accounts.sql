-- Local test identities are separate from shared demo and external provider identities.
CREATE TABLE local_user_account (
  user_id bigint NOT NULL PRIMARY KEY,
  username varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  password_hash varchar(255) NOT NULL,
  must_change_password boolean NOT NULL DEFAULT true,
  created_at datetime NOT NULL,
  UNIQUE KEY uq_local_user_username(username)
);
