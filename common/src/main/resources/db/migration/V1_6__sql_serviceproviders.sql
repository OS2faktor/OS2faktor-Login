CREATE TABLE sql_service_provider_configuration (
    id                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    name                VARCHAR(255) NOT NULL,
    entity_id           VARCHAR(255) NOT NULL,
    metadata_url        VARCHAR(255) NOT NULL,
    name_id_format      VARCHAR(255) NOT NULL,
    name_id_value       VARCHAR(255) NOT NULL,
    force_mfa_required  VARCHAR(255) NOT NULL,
    nsis_level_required VARCHAR(255) NULL,
    prefer_nemid        BOOLEAN NOT NULL
);

CREATE TABLE sql_service_provider_required_fields (
    id                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    configuration_id    BIGINT NOT NULL,
    person_field        VARCHAR(255) NOT NULL,
    attribute_name      VARCHAR(255) NOT NULL,

    FOREIGN KEY (configuration_id) REFERENCES sql_service_provider_configuration(id)
);

CREATE TABLE sql_service_provider_static_claims (
    id                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    configuration_id    BIGINT NOT NULL,
    claim_field         VARCHAR(255) NOT NULL,
    claim_value         VARCHAR(255) NOT NULL,

    FOREIGN KEY (configuration_id) REFERENCES sql_service_provider_configuration(id)
);