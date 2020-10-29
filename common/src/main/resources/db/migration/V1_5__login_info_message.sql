CREATE TABLE login_info_message (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  message                      TEXT,
  enabled                      BOOLEAN NOT NULL DEFAULT 0
);
