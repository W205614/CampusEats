CREATE TABLE stored_image (
  id CHAR(36) NOT NULL PRIMARY KEY,
  backend VARCHAR(10) NOT NULL,
  visibility VARCHAR(10) NOT NULL,
  bucket_name VARCHAR(100) NULL,
  object_key VARCHAR(200) NULL,
  object_version VARCHAR(200) NULL,
  byte_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  owner_type VARCHAR(10) NOT NULL,
  owner_id BIGINT NOT NULL,
  store_id BIGINT NULL,
  state VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT ck_stored_image_bytes CHECK(byte_size>0 AND byte_size<=5242880),
  CONSTRAINT ck_stored_image_backend CHECK(backend IN('LOCAL','COS')),
  CONSTRAINT ck_stored_image_visibility CHECK(visibility IN('PUBLIC','PRIVATE')),
  CONSTRAINT ck_stored_image_state CHECK(state IN('UPLOADING','READY','UNKNOWN','UNREFERENCED')),
  INDEX ix_stored_image_state(state,created_at),
  INDEX ix_stored_image_owner(owner_type,owner_id)
);
CREATE TABLE blob_storage_budget (
  id TINYINT NOT NULL PRIMARY KEY,
  used_bytes BIGINT NOT NULL DEFAULT 0,
  public_count BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_blob_budget CHECK(id=1 AND used_bytes>=0 AND public_count>=0)
);
INSERT INTO blob_storage_budget(id) VALUES(1);
