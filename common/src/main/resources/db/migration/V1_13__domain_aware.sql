CREATE TABLE domains (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  name                         VARCHAR(255) NOT NULL,

  CONSTRAINT c_domains_name UNIQUE (name)
);

CREATE TABLE domains_aud (
  id                           BIGINT NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,

  name                         VARCHAR(255) NULL,

  FOREIGN KEY fk_supporters_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_supporters_aud (id, rev)
);

CREATE TABLE supporters (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  domain_id                    BIGINT NOT NULL,
  person_id                    BIGINT NOT NULL,

  CONSTRAINT fk_supporters_domains FOREIGN KEY (domain_id) REFERENCES domains(id),
  CONSTRAINT fk_supporters_persons FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE supporters_aud (
  id                           BIGINT NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT,

  domain_id                    BIGINT NULL,
  person_id                    BIGINT NULL,

  FOREIGN KEY fk_supporters_aud_rev (rev) REFERENCES revinfo(id),
  PRIMARY KEY pk_supporters_aud (id, rev)
);

-- If any domain has been added to persons add it to the new domains table
INSERT INTO domains(name) SELECT DISTINCT(`domain`) AS domain FROM persons ORDER BY domain DESC;

-- copy existing session settings to all domains
ALTER TABLE session_settings ADD COLUMN domain_id BIGINT NULL;
ALTER TABLE session_settings ADD CONSTRAINT fk_session_domains FOREIGN KEY (domain_id) REFERENCES domains(id);
INSERT INTO session_settings (password_expiry, mfa_expiry, domain_id) SELECT s.password_expiry, s.mfa_expiry, d.id FROM session_settings s JOIN domains d;
DELETE FROM session_settings WHERE domain_id IS NULL;
ALTER TABLE session_settings MODIFY COLUMN domain_id BIGINT NOT NULL;

-- copy existing password settings to all domains
ALTER TABLE password_settings ADD COLUMN domain_id BIGINT NULL;
ALTER TABLE password_settings ADD CONSTRAINT fk_password_domains FOREIGN KEY (domain_id) REFERENCES domains(id);
INSERT INTO password_settings (min_length, capital_and_small_letters, digits, special_characters, force_change_password_enabled, force_change_password_interval, disallow_old_passwords,
                               replicate_to_ad_enabled, cache_ad_password_interval, validate_against_ad_enabled, monitoring_enabled, monitoring_email, disallow_danish_characters, domain_id)
       SELECT min_length, capital_and_small_letters, digits, special_characters, force_change_password_enabled, force_change_password_interval, disallow_old_passwords, replicate_to_ad_enabled,
              cache_ad_password_interval, validate_against_ad_enabled, monitoring_enabled, monitoring_email, disallow_danish_characters, d.id FROM password_settings s JOIN domains d;
DELETE FROM password_settings WHERE domain_id IS NULL;
ALTER TABLE password_settings MODIFY COLUMN domain_id BIGINT NOT NULL;

-- Update persons to use key instead of string
ALTER TABLE persons ADD COLUMN domain_id BIGINT NULL;
UPDATE persons p
JOIN (SELECT id, name FROM domains) d ON p.domain = d.name
SET p.domain_id  = d.id;
ALTER TABLE persons MODIFY COLUMN domain_id BIGINT NOT NULL;

-- Ensure _aud tables are correct
ALTER TABLE persons ADD CONSTRAINT fk_persons_domains FOREIGN KEY (domain_id) REFERENCES domains(id);
ALTER TABLE persons DROP COLUMN `domain`;
ALTER TABLE persons DROP COLUMN `supporter`;

ALTER TABLE persons_aud ADD COLUMN domain_id BIGINT NULL AFTER `domain`;
ALTER TABLE persons_aud DROP COLUMN `domain`;
ALTER TABLE persons_aud DROP COLUMN `supporter`;

ALTER TABLE auditlogs ADD COLUMN person_domain VARCHAR(255) NULL;
UPDATE auditlogs a SET person_domain = (select d.name FROM domains d JOIN persons p WHERE p.domain_id = d.id AND p.id = a.person_id);
