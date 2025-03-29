TRUNCATE oauth2_authorization;
CREATE INDEX idx_oauth2_auth_rtia ON oauth2_authorization (refresh_token_issued_at);
CREATE INDEX idx_oauth2_auth_acia ON oauth2_authorization (authorization_code_issued_at);
CREATE INDEX idx_oauth2_auth_atea ON oauth2_authorization (access_token_expires_at);
