ALTER TABLE sql_service_provider_configuration MODIFY COLUMN delayed_mobile_login BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE kombit_subsystems MODIFY COLUMN delayed_mobile_login BOOLEAN NOT NULL DEFAULT false;
UPDATE kombit_subsystems SET delayed_mobile_login = 0 WHERE entity_id NOT LIKE '%nexus.kmd.dk%';
