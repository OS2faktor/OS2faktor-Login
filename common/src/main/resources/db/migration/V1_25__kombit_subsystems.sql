CREATE TABLE kombit_subsystems (
  id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  entity_id               VARCHAR(255) NOT NULL,
  name                    VARCHAR(255) NULL,
  rc_identifier           VARCHAR(255) NULL,
  min_nsis_level          VARCHAR(64) NULL,
  always_require_mfa      BOOLEAN NOT NULL DEFAULT 0,
  deleted                 BOOLEAN NOT NULL DEFAULT 0,
  
  UNIQUE(entity_id)
);
