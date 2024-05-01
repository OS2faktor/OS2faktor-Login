CREATE TABLE keystores (
   id                         BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   subject_dn                 VARCHAR(255) NOT NULL,
   primary_for_idp            BOOLEAN NOT NULL,
   primary_for_nem_login      BOOLEAN NOT NULL,
   expires                    DATE NOT NULL,
   keystore                   BLOB NOT NULL,
   password                   VARCHAR(64) NOT NULL,
   last_updated               DATETIME NOT NULL,
   disabled                   BOOLEAN NOT NULL
);