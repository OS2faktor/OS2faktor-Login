CREATE TABLE cms_messages (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  cms_key                      VARCHAR(255) NOT NULL,
  cms_value                    TEXT NOT NULL,
  last_updated                 DATETIME NULL
);