CREATE TABLE radius_client_claim (
  id                                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  radius_client_id                          BIGINT NOT NULL,
  person_field                              VARCHAR(255) NOT NULL,
  attribute_id                              BIGINT NOT NULL DEFAULT 0,

  CONSTRAINT fk_radius_client_claim FOREIGN KEY (radius_client_id) REFERENCES radius_clients(id) ON DELETE CASCADE
);
