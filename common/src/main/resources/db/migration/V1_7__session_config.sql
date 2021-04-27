CREATE TABLE session_settings (
  id                               BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  password_expiry                  BIGINT NOT NULL,
  mfa_expiry                       BIGINT NOT NULL
);

INSERT INTO session_settings (password_expiry, mfa_expiry) VALUES (180, 60);