CREATE TABLE certificate_changelog (
   id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   tts                       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
   ip_address                VARCHAR(64) NOT NULL,
   operator_id               VARCHAR(64) NOT NULL,
   change_type               VARCHAR(64) NOT NULL,
   details                   VARCHAR(1024) NOT NULL
);
