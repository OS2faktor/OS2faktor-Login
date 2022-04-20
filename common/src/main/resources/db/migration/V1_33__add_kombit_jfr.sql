CREATE TABLE person_kombit_jfr (
    id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    person_id                     BIGINT NOT NULL,
    identifier                    VARCHAR(255) NOT NULL,
    cvr                           VARCHAR(8) NOT NULL,
    
    FOREIGN KEY fk_person_kombit_jfr_person (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE person_kombit_jfr_aud (
    id                            BIGINT NOT NULL,
    rev                           BIGINT NOT NULL,
    revtype                       TINYINT,
    
    person_id                     BIGINT,
    identifier                    VARCHAR(255),
    cvr                           VARCHAR(8),
    
    FOREIGN KEY fk_person_kombit_jfr_aud_rev (rev) REFERENCES revinfo(id),
    PRIMARY KEY pk_person_kombit_jfr_aud (id, rev)
);