CREATE TABLE sql_service_provider_mfa_exempted_domain (
    id                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    configuration_id    BIGINT NOT NULL,
    domain_id           BIGINT NULL,

    CONSTRAINT fk_sql_service_provider_mfa_exempted_domain_1 FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE,
    CONSTRAINT fk_sql_service_provider_mfa_exempted_domain_2 FOREIGN KEY (configuration_id) REFERENCES sql_service_provider_configuration(id) ON DELETE CASCADE
);
