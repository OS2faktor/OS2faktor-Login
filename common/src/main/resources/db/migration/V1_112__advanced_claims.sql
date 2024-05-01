CREATE TABLE sql_service_provider_adv_claims (
    id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    configuration_id              BIGINT NOT NULL,
    claim_name                    VARCHAR(255) NOT NULL,
    claim_value                   TEXT NULL,

    FOREIGN KEY (configuration_id) REFERENCES sql_service_provider_configuration(id)
);
