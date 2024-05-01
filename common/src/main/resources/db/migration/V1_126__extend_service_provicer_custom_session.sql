ALTER TABLE sql_service_provider_configuration ADD COLUMN custom_password_expiry BIGINT NULL;
ALTER TABLE sql_service_provider_configuration ADD COLUMN custom_mfa_expiry      BIGINT NULL;