CREATE TABLE sql_service_provider_rc_claims (
    id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    configuration_id              BIGINT NOT NULL,
    external_operation            VARCHAR(255) NOT NULL,
    external_operation_argument   VARCHAR(255) NULL,
    claim_name                    VARCHAR(255) NOT NULL,
    claim_value                   VARCHAR(255) NULL,

    FOREIGN KEY (configuration_id) REFERENCES sql_service_provider_configuration(id)
);
