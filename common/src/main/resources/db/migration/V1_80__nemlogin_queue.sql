ALTER TABLE persons ADD COLUMN nemlogin_user_uuid VARCHAR(36) NULL;
ALTER TABLE persons_aud ADD COLUMN nemlogin_user_uuid VARCHAR(36) NULL;

CREATE TABLE nemlogin_queue (
	id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	person_id                        BIGINT NULL,
	nemlogin_user_uuid               VARCHAR(36) NULL,
	action                           VARCHAR(255) NOT NULL,
	failed                           BOOL NOT NULL DEFAULT 0,
	failure_reason                   VARCHAR(255) NULL,
	tts                              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	
	CONSTRAINT fk_nemlogin_queue_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);