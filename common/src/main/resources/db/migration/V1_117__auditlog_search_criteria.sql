CREATE TABLE audit_log_search_criteria (
   id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   name                          VARCHAR(255),
   description                   VARCHAR(500),
   log_action_filter             VARCHAR(64) NOT NULL,
   message_filter                VARCHAR(255)
);
