
-- these fields are not relevant for audit, and they change very often
ALTER TABLE persons_aud
  DROP COLUMN locked_password,
  DROP COLUMN locked_password_until,
  DROP COLUMN bad_password_count,
  DROP COLUMN next_password_change,
  DROP COLUMN daily_password_change_counter;

-- we no longer keep audit on these relations
DROP TABLE persons_attributes_aud;
DROP TABLE persons_kombit_attributes_aud;
DROP TABLE school_roles_aud;
DROP TABLE cached_mfa_client_aud;
DROP TABLE person_kombit_jfr_aud;
DROP TABLE persons_groups_aud;
DROP TABLE ggroups_aud;
DROP TABLE school_roles_school_classes_aud;
DROP TABLE school_classes_aud;
DROP TABLE school_class_password_word_aud;
