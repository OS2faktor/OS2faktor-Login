CREATE TABLE log_watch_settings (
   id                         BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   setting_key                 VARCHAR(255) NOT NULL,
   setting_value               VARCHAR(255) NOT NULL
);

ALTER TABLE auditlogs ADD COLUMN location VARCHAR(255) NULL;

CREATE INDEX idx_auditlog_log_action ON auditlogs (log_action); 