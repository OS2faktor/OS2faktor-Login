CREATE TABLE password_validation_filter_api_key (
	id 					BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	api_key				VARCHAR(255) NOT NULL,
	disabled			BOOLEAN NOT NULL DEFAULT 0,
    domain_id           BIGINT NOT NULL,
	description   		VARCHAR(255) NULL,

    CONSTRAINT fk_password_validation_filter_api_key_domains FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);
