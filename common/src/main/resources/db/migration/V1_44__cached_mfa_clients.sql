CREATE TABLE cached_mfa_client (
    id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    name                         VARCHAR(255) NOT NULL,
    type                         VARCHAR(64) NOT NULL,
    device_id                    VARCHAR(36) NOT NULL,
    nsis_level                   VARCHAR(64) NOT NULL,
    person_id                    BIGINT NOT NULL,

    FOREIGN KEY fk_person_cached_mfa_client (person_id) REFERENCES persons(id)
);

CREATE TABLE cached_mfa_client_aud (
    id                            BIGINT NOT NULL,
    rev                           BIGINT NOT NULL,
    revtype                       TINYINT,

    name                          VARCHAR(255),
    type                          VARCHAR(64),
    device_id                     VARCHAR(36),
    nsis_level                    VARCHAR(64),
    person_id                     BIGINT,
    
    FOREIGN KEY fk_cached_mfa_client_aud_rev (rev) REFERENCES revinfo(id),
    PRIMARY KEY pk_cached_mfa_client_aud (id, rev)
);