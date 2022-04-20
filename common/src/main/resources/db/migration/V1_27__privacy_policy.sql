CREATE TABLE privacy_policy (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  content                      TEXT
);

CREATE TABLE privacy_policy_aud (
  id                           BIGINT NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,
  
  content                      TEXT,

  FOREIGN KEY fk_privacy_policy_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_privacy_policy_aud (id, rev)
)
