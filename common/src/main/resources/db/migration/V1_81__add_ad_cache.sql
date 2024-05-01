ALTER TABLE persons DROP COLUMN ad_password;
ALTER TABLE persons_aud DROP COLUMN ad_password;

CREATE TABLE ad_password_cache (
	id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	domain_id                        BIGINT NULL,
	sam_account_name                 VARCHAR(255) NOT NULL,
	password                         VARCHAR(255) NOT NULL,
	last_updated                     DATETIME NOT NULL
);
