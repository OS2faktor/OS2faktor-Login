CREATE TABLE session_temporary_client_mapping (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  session_id                   VARCHAR(36) NOT NULL,
  temporary_client_id          BIGINT NOT NULL,

  FOREIGN KEY fk_temporary_client (temporary_client_id) REFERENCES temporary_client_session_keys(id) ON DELETE CASCADE
);