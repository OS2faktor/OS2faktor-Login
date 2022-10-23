CREATE TABLE persons_kombit_attributes (
  person_id                    BIGINT NOT NULL,
  attribute_key                VARCHAR(255) NOT NULL,
  attribute_value              TEXT NOT NULL,

  PRIMARY KEY (person_id, attribute_key),
  FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE persons_kombit_attributes_aud (
  id                           BIGINT NOT NULL AUTO_INCREMENT,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,

  person_id                    BIGINT,
  attribute_key                VARCHAR(255),
  attribute_value              TEXT,

  FOREIGN KEY (rev) REFERENCES revinfo(id),
  PRIMARY KEY (id, rev)
);
