CREATE TABLE known_networks (
   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   ip VARCHAR(255) NOT NULL
);

-- Create new enum
ALTER TABLE kombit_subsystems ADD COLUMN force_mfa_required VARCHAR(255) NOT NULL AFTER min_nsis_level;

-- Migrate old boolean always_require_mfa to new enum
UPDATE kombit_subsystems SET force_mfa_required="ALWAYS" WHERE always_require_mfa IS TRUE;
UPDATE kombit_subsystems SET force_mfa_required="DEPENDS" WHERE always_require_mfa IS FALSE;

-- Drop old column after migration
ALTER TABLE kombit_subsystems DROP COLUMN always_require_mfa;

-- Fix sql service providers after removing NEVER as an option
UPDATE sql_service_provider_configuration SET force_mfa_required = 'DEPENDS' WHERE force_mfa_required = 'NEVER';
