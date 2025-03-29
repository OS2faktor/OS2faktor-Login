ALTER TABLE sql_service_provider_rc_claims ADD COLUMN single_value_only BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE sql_service_provider_adv_claims ADD COLUMN single_value_only BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE sql_service_provider_group_claims ADD COLUMN single_value_only BOOLEAN NOT NULL DEFAULT 0;