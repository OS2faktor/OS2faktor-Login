CREATE TABLE groups (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  name                         VARCHAR(255) NOT NULL,
  uuid                         VARCHAR(255) NOT NULL,
  domain_id                    BIGINT NOT NULL,
  description                  TEXT NULL,

  CONSTRAINT fk_group_domain   FOREIGN KEY (domain_id) REFERENCES domains(id)
);

CREATE TABLE groups_aud (
  id                           BIGINT NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,

  name                         VARCHAR(255),
  uuid                         VARCHAR(255),
  domain_id                    BIGINT,
  description                  TEXT,

  FOREIGN KEY fk_groups_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_groups_aud (id, rev)
);

CREATE TABLE persons_groups (
  id                                    BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  group_id                              BIGINT NOT NULL,
  person_id                             BIGINT NOT NULL,

  CONSTRAINT fk_persons_groups_group    FOREIGN KEY (group_id)  REFERENCES groups(id) ON DELETE CASCADE,
  CONSTRAINT fk_persons_groups_person   FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE persons_groups_aud (
  id                           BIGINT NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,

  group_id                     BIGINT NULL,
  person_id                    BIGINT NULL,

  FOREIGN KEY fk_persons_groups_aud_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_persons_groups_aud_aud (id, rev)
);

ALTER TABLE password_settings ADD COLUMN        change_password_on_users_enabled    BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE password_settings ADD COLUMN        change_password_on_users_group_id   BIGINT  NULL;
ALTER TABLE password_settings ADD CONSTRAINT    fk_password_settings_groups         FOREIGN KEY (change_password_on_users_group_id) REFERENCES groups(id) ON DELETE SET NULL;

CREATE TABLE sql_service_provider_condition (
  id                                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  sql_service_provider_configuration_id     BIGINT NOT NULL,
  type                                      VARCHAR(255) NOT NULL,
  domain_id                                 BIGINT NULL,
  group_id                                  BIGINT NULL,

  CONSTRAINT fk_sql_service_provider_condition          FOREIGN KEY (sql_service_provider_configuration_id) REFERENCES sql_service_provider_configuration(id) ON DELETE CASCADE,
  CONSTRAINT fk_sql_service_provider_condition_domain   FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE,
  CONSTRAINT fk_sql_service_provider_condition_group    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);

CREATE TABLE radius_client_condition (
  id                                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  radius_client_id                          BIGINT NOT NULL,
  type                                      VARCHAR(255) NOT NULL,
  domain_id                                 BIGINT NULL,
  group_id                                  BIGINT NULL,

  CONSTRAINT fk_radius_client_condition          FOREIGN KEY (radius_client_id) REFERENCES radius_clients(id) ON DELETE CASCADE,
  CONSTRAINT fk_radius_client_condition_domain   FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE,
  CONSTRAINT fk_radius_client_condition_group    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);

SELECT CONCAT(
	'ALTER TABLE `radius_clients` DROP FOREIGN KEY `',
	constraint_name,
	'`'
) INTO @sqlst
	FROM information_schema.KEY_COLUMN_USAGE
	WHERE table_name = 'radius_clients'
		AND referenced_table_name='domains'
		AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE radius_clients DROP COLUMN can_be_used_by;
ALTER TABLE radius_clients DROP COLUMN domain_id;


INSERT INTO domains(name, monitored) VALUES('OS2faktor', 0);

