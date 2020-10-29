CREATE TABLE password_settings (
  id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  min_length                       BIGINT NOT NULL,
  capital_and_small_letters        BOOLEAN NOT NULL DEFAULT 0,
  digits                           BOOLEAN NOT NULL DEFAULT 0,
  special_characters               BOOLEAN NOT NULL DEFAULT 0,
  force_change_password_enabled    BOOLEAN NOT NULL DEFAULT 0,
  force_change_password_interval   BIGINT NOT NULL DEFAULT 90,
  replicate_to_ad_enabled          BOOLEAN NOT NULL DEFAULT 0,
  validate_against_ad_enabled      BOOLEAN NOT NULL DEFAULT 0,
  monitoring_enabled               BOOLEAN NOT NULL DEFAULT 0,
  monitoring_email                 VARCHAR(255)
);

INSERT INTO password_settings (min_length, capital_and_small_letters) VALUES (10, 1);

CREATE TABLE password_change_queue (
  id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  cpr                              VARCHAR(10) NOT NULL,
  samaccount_name                  VARCHAR(255) NOT NULL,
  password                         VARCHAR(255) NOT NULL,
  tts                              DATETIME NULL,
  status                           VARCHAR(255) NULL,
  message                          TEXT
)