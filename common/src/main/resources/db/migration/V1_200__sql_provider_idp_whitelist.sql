ALTER TABLE sql_service_provider_configuration ADD COLUMN nsis_idp_login_filter VARCHAR(255) NULL;
ALTER TABLE sql_service_provider_configuration MODIFY COLUMN name_id_format VARCHAR(255) NOT NULL DEFAULT 'PERSISTENT';
ALTER TABLE sql_service_provider_configuration MODIFY COLUMN force_mfa_required VARCHAR(255) NOT NULL DEFAULT 'DEPENDS';
ALTER TABLE sql_service_provider_configuration MODIFY COLUMN prefer_nemid BOOLEAN NOT NULL DEFAULT 0;
