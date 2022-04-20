CREATE TABLE radius_clients (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  name                         VARCHAR(255) NOT NULL,
  password                     VARCHAR(128) NOT NULL,
  ip_address                   VARCHAR(128) NOT NULL,
  can_be_used_by               VARCHAR(128) NOT NULL,
  domain_id                    BIGINT NULL,
  
  FOREIGN KEY fk_radius_clients__domain (domain_id) REFERENCES domains(id)
);