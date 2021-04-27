CREATE TABLE local_registered_mfa_clients (
	id 					BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	cpr					VARCHAR(10) NOT NULL,
	name				VARCHAR(255) NOT NULL,
	type				VARCHAR(64) NOT NULL,
	device_id			VARCHAR(36) NOT NULL,
	nsis_level          VARCHAR(64) NOT NULL
);