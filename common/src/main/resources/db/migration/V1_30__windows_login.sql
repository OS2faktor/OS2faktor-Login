CREATE TABLE windows_credential_provider_clients (
	id 					BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	name    		    VARCHAR(255) NOT NULL,
	api_key				VARCHAR(255) NOT NULL,
	disabled			BOOLEAN NOT NULL DEFAULT 0
);

CREATE TABLE temporary_client_session_keys (
	id 					BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	tts     			DATETIME NOT NULL,
	person_id    		BIGINT NOT NULL,
    nsis_level          VARCHAR(64) NOT NULL,
	session_key			VARCHAR(255) NOT NULL,

    FOREIGN KEY (person_id) REFERENCES persons(id)
);