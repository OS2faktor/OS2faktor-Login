CREATE TABLE login_alarms (
   id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   country                       VARCHAR(255),
   ip_address                    VARCHAR(255),
   alarm_type                    VARCHAR(64) NOT NULL,
   tts                           DATETIME NOT NULL,
   person_id                     BIGINT NOT NULL,
   
   CONSTRAINT fk_fla_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);
