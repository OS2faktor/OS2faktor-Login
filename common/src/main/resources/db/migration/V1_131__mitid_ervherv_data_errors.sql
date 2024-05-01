CREATE TABLE mitid_erhverv_account_errors (
    id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    person_id                    BIGINT NOT NULL,
    error_type                   VARCHAR(64) NOT NULL,
    tts                          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nemlogin_user_uuid           VARCHAR(36),

    FOREIGN KEY fk_miae_person (person_id) REFERENCES persons(id) ON DELETE CASCADE
);