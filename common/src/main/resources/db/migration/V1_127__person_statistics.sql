CREATE TABLE person_statistics (
   id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   person_id                 BIGINT NOT NULL,
   last_login                DATETIME NULL,
   last_self_service_login   DATETIME NULL,
   last_password_change      DATETIME NULL,
   last_unlock               DATETIME NULL,
   last_mfa_use              DATETIME NULL,

   CONSTRAINT fk_person_statistics_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

ALTER TABLE cached_mfa_client ADD last_used DATETIME NULL;