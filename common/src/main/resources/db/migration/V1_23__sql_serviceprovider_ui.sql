ALTER TABLE persons ADD COLUMN service_provider_admin BOOLEAN NOT NULL DEFAULT 0 AFTER admin;
ALTER TABLE persons_aud ADD COLUMN service_provider_admin BOOLEAN AFTER admin;

ALTER TABLE sql_service_provider_configuration MODIFY metadata_url VARCHAR(255) NULL;
ALTER TABLE sql_service_provider_configuration ADD COLUMN protocol VARCHAR(64) NOT NULL DEFAULT 'SAML20';
ALTER TABLE sql_service_provider_configuration ADD COLUMN last_updated DATETIME NULL DEFAULT CURRENT_TIMESTAMP;