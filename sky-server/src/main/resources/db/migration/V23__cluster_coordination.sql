CREATE TABLE catalog_cache_version (
  cache_key VARCHAR(160) NOT NULL PRIMARY KEY,
  generation BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE production_bootstrap_lock (
  id TINYINT NOT NULL PRIMARY KEY,
  initialized_at DATETIME(6) NULL
) ENGINE=InnoDB;
INSERT INTO production_bootstrap_lock(id) VALUES(1);

CREATE TABLE cluster_job_lease (
  job_name VARCHAR(80) NOT NULL PRIMARY KEY,
  owner_id VARCHAR(80) NOT NULL,
  lease_token VARCHAR(36) NOT NULL,
  lease_until DATETIME(6) NOT NULL,
  last_started_at DATETIME(6) NOT NULL,
  last_completed_at DATETIME(6) NULL,
  last_success_at DATETIME(6) NULL,
  last_error VARCHAR(120) NULL
) ENGINE=InnoDB;
