CREATE TABLE terms_and_conditions (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  content                      TEXT
);

CREATE TABLE terms_and_conditions_aud (
  id                           BIGINT NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,
  
  content                      TEXT,

  FOREIGN KEY fk_users_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_users_aud (id, rev)
)
