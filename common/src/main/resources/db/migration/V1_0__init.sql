CREATE TABLE revinfo (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  revtstmp                     BIGINT NOT NULL
);

CREATE TABLE persons (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  uuid                         VARCHAR(36) NOT NULL,
  cpr                          VARCHAR(10) NOT NULL,
  name                         VARCHAR(255) NOT NULL,
  email                        VARCHAR(255),
  nsis_level                   VARCHAR(64) NOT NULL,
  nsis_allowed                 BOOLEAN NOT NULL DEFAULT 0,
  admin                        BOOLEAN NOT NULL DEFAULT 0,
  supporter                    BOOLEAN NOT NULL DEFAULT 0,
  approved_conditions          BOOLEAN NOT NULL DEFAULT 0,
  approved_conditions_tts      DATETIME NULL,
  locked_admin                 BOOLEAN NOT NULL DEFAULT 0,
  locked_person                BOOLEAN NOT NULL DEFAULT 0,
  locked_dataset               BOOLEAN NOT NULL DEFAULT 0,
  locked_password              BOOLEAN NOT NULL DEFAULT 0,
  locked_password_until        DATETIME NULL,
  bad_password_count           BIGINT NOT NULL DEFAULT 0,
  user_id                      VARCHAR(255),
  nsis_password                VARCHAR(255),
  nsis_password_timestamp      DATETIME NULL,
  ad_password                  VARCHAR(255),
  ad_password_timestamp        DATETIME NULL,
  samaccount_name              VARCHAR(255),
  domain                       VARCHAR(255),
  nem_id_pid                   VARCHAR(255),

  CONSTRAINT c_users_userid UNIQUE (user_id)
);

CREATE TABLE persons_aud (
  id                           BIGINT NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,

  uuid                         VARCHAR(36),
  cpr                          VARCHAR(10),
  name                         VARCHAR(255),
  email                        VARCHAR(255),
  nsis_level                   VARCHAR(64),
  nsis_allowed                 BOOLEAN NULL,
  locked_admin                 BOOLEAN NULL,
  locked_person                BOOLEAN NULL,
  locked_dataset               BOOLEAN NULL,
  locked_password              BOOLEAN NULL,
  locked_password_until        DATETIME NULL,
  bad_password_count           BIGINT NULL,
  admin                        BOOLEAN NULL,
  supporter                    BOOLEAN NULL,
  approved_conditions          BOOLEAN NULL,
  approved_conditions_tts      DATETIME NULL,
  samaccount_name              VARCHAR(255),
  domain                       VARCHAR(255),
  user_id                      VARCHAR(255),
  nsis_password                VARCHAR(255),
  nsis_password_timestamp      DATETIME NULL,
  ad_password                  VARCHAR(255),
  ad_password_timestamp        DATETIME NULL,
  nem_id_pid                   VARCHAR(255),

  FOREIGN KEY fk_persons_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_persons_aud (id, rev)
);

CREATE TABLE persons_attributes (
  person_id                    BIGINT NOT NULL,
  attribute_key                VARCHAR(255) NOT NULL,
  attribute_value              TEXT NOT NULL,

  PRIMARY KEY (person_id, attribute_key),
  FOREIGN KEY (person_id) REFERENCES persons(id)
);

CREATE TABLE persons_attributes_aud (
  id                           BIGINT NOT NULL AUTO_INCREMENT,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,

  person_id                    BIGINT,
  attribute_key                VARCHAR(255),
  attribute_value              TEXT,

  FOREIGN KEY fk_persons_attr_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_persons_attr_aud (id, rev)
);
