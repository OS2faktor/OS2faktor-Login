CREATE TABLE password_settings (
  id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  min_length                       BIGINT NOT NULL,
  capital_and_small_letters        BOOLEAN NOT NULL DEFAULT 0,
  digits                           BOOLEAN NOT NULL DEFAULT 0,
  special_characters               BOOLEAN NOT NULL DEFAULT 0,
  force_change_password_enabled    BOOLEAN NOT NULL DEFAULT 0,
  force_change_password_interval   BIGINT NOT NULL DEFAULT 90,
  disallow_old_passwords           BOOLEAN NOT NULL DEFAULT 0,
  replicate_to_ad_enabled          BOOLEAN NOT NULL DEFAULT 0,
  cache_ad_password_interval       BIGINT NOT NULL DEFAULT 0,
  validate_against_ad_enabled      BOOLEAN NOT NULL DEFAULT 0,
  monitoring_enabled               BOOLEAN NOT NULL DEFAULT 0,
  monitoring_email                 VARCHAR(255)
);

INSERT INTO password_settings (min_length, capital_and_small_letters) VALUES (10, 1);

CREATE TABLE password_change_queue (
  id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  samaccount_name                  VARCHAR(255) NOT NULL,
  domain                           VARCHAR(255) NOT NULL,
  password                         VARCHAR(255) NOT NULL,
  tts                              DATETIME NULL,
  status                           VARCHAR(255) NULL,
  message                          TEXT
);

CREATE TABLE password_history (
  id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  person_id                        BIGINT NOT NULL,
  password                         VARCHAR(255) NOT NULL
);