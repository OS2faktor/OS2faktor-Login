CREATE TABLE mfa_login_history (
  id            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  server_name   VARCHAR(255) NOT NULL,
  device_id     VARCHAR(64) NOT NULL,
  status        VARCHAR(64) NOT NULL,
  created_tts   DATETIME NOT NULL,
  push_tts      DATETIME NULL,
  fetch_tts     DATETIME NULL,
  response_tts  DATETIME NULL,
  client_type   VARCHAR(64),
  
  INDEX idx_created (created_tts),
  INDEX idx_device_id (device_id)
);
