CREATE TABLE email_templates (
	id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  	title                        VARCHAR(255) NOT NULL,
  	message                      MEDIUMTEXT NOT NULL,
  	template_type                VARCHAR(64) NOT NULL,
  	enabled                      BOOLEAN NOT NULL DEFAULT FALSE,
  	eboks                        BOOLEAN NOT NULL DEFAULT FALSE,
  	email                        BOOLEAN NOT NULL DEFAULT FALSE
);