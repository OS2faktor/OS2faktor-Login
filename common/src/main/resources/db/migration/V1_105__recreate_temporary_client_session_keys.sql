DROP TABLE IF EXISTS session_temporary_client_mapping;
DROP TABLE IF EXISTS temporary_client_session_keys;

CREATE TABLE temporary_client_session_keys (
	id 					BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	tts     			DATETIME NOT NULL,
	person_id    		BIGINT NOT NULL,
    nsis_level          VARCHAR(64) NOT NULL,
	session_key			VARCHAR(255) NOT NULL,

    FOREIGN KEY fk_tcsk_persons (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE session_temporary_client_mapping (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  session_id                   VARCHAR(36) NOT NULL,
  temporary_client_id          BIGINT NOT NULL,

  FOREIGN KEY fk_temporary_client (temporary_client_id) REFERENCES temporary_client_session_keys(id) ON DELETE CASCADE
);