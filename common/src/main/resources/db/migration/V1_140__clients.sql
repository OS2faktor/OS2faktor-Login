CREATE TABLE clients (
   id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   api_key                   VARCHAR(255) NOT NULL,
   role                      VARCHAR(255) NOT NULL,
   description               TEXT NULL
);
