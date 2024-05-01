CREATE TABLE sql_service_provider_group_claims (
    id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    configuration_id              BIGINT NOT NULL,
    claim_name                    VARCHAR(255) NOT NULL,
    claim_value                   VARCHAR(255) NOT NULL,
    group_id                      BIGINT NOT NULL,

    FOREIGN KEY (configuration_id) REFERENCES sql_service_provider_configuration(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES ggroups(id) ON DELETE CASCADE
);
