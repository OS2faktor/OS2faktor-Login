ALTER TABLE oauth2_authorization MODIFY COLUMN authorization_code_value VARCHAR(1024) NULL;
CREATE INDEX idx_oauth2_authorization_acv ON oauth2_authorization (authorization_code_value);
