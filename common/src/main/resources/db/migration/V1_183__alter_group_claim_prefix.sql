ALTER TABLE sql_service_provider_group_claims MODIFY COLUMN group_id BIGINT(20) NULL;
ALTER TABLE sql_service_provider_group_claims ADD COLUMN value_prefix BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE sql_service_provider_group_claims ADD COLUMN remove_prefix BOOLEAN NOT NULL DEFAULT 0;