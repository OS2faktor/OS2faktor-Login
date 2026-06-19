CREATE TABLE password_sync_queue (
    id                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    tts                 DATETIME NOT NULL,
    next_attempt_at     DATETIME NOT NULL,
    encrypted_password  VARCHAR(255) NOT NULL,
    person_id           BIGINT NOT NULL,
    status              VARCHAR(255) NOT NULL,

    CONSTRAINT fk_password_sync_queue__person
        FOREIGN KEY (person_id)
        REFERENCES persons(id)
);
