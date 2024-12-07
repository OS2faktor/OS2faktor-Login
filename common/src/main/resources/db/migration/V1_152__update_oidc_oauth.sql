ALTER TABLE oauth2_registered_client ADD post_logout_redirect_uris varchar(1000) DEFAULT NULL;

ALTER TABLE oauth2_authorization ADD authorized_scopes varchar(1000) DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD oidc_id_token_claims varchar(2000) DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD user_code_value varchar(4000) DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD user_code_issued_at timestamp DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD user_code_expires_at timestamp DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD user_code_metadata varchar(2000) DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD device_code_value TEXT DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD device_code_issued_at timestamp DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD device_code_expires_at timestamp DEFAULT NULL;
ALTER TABLE oauth2_authorization ADD device_code_metadata TEXT DEFAULT NULL;
