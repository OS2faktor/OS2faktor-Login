CREATE TABLE auditlogs_details (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  detail_type                  VARCHAR(64) NOT NULL,
  detail_content               TEXT,
  detail_supplement            TEXT
);

CREATE TABLE auditlogs (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  
  -- metadata
  tts                          DATETIME NULL,
  ip_address                   VARCHAR(255),
  correlation_id               VARCHAR(255),

  -- referenced person
  person_id                    BIGINT NOT NULL,
  person_name                  VARCHAR(255) NOT NULL,
  cpr                          VARCHAR(10) NOT NULL,
  
  -- referenced performer
  performer_id                 BIGINT,
  performer_name               VARCHAR(255),

  -- structured log-data
  log_action                   VARCHAR(64) NOT NULL,
  log_target_id                VARCHAR(255),
  log_target_name              VARCHAR(255),
  
  -- abbreviated human-readable message
  message                      VARCHAR(500),
  
  -- full details (lazy-loaded by code)
  auditlogs_details_id         BIGINT,
  
  FOREIGN KEY fk_auditlogs_person (person_id) REFERENCES persons(id) ON DELETE CASCADE,
  FOREIGN KEY fk_auditlogs_details (auditlogs_details_id) REFERENCES auditlogs_details (id),
  INDEX(cpr),
  INDEX(tts)
);
