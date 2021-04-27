ALTER TABLE sql_service_provider_configuration ADD COLUMN encrypt_assertions BOOLEAN NOT NULL DEFAULT 1;
ALTER TABLE sql_service_provider_configuration ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT 1;