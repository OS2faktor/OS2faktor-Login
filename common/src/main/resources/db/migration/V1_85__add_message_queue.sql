CREATE table message_queue (
   id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   subject                      VARCHAR(255) NOT NULL,
   message                      VARCHAR(255) NOT NULL,
   cpr                          VARCHAR(10),
   email                        VARCHAR(255),
   person_id                    BIGINT NOT NULL,
   delivery_tts                 DATETIME NOT NULL,
   email_template_id            BIGINT NULL,

   CONSTRAINT fk_message_queue_email_template FOREIGN KEY (email_template_id) REFERENCES email_templates(id)
);