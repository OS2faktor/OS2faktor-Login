ALTER TABLE persons     RENAME COLUMN nsis_password TO password;
ALTER TABLE persons     RENAME COLUMN nsis_password_timestamp TO password_timestamp;
ALTER TABLE persons_aud RENAME COLUMN nsis_password TO password;
ALTER TABLE persons_aud RENAME COLUMN nsis_password_timestamp TO password_timestamp;
ALTER TABLE persons     DROP COLUMN next_password_change;

DROP TABLE ad_password_cache;

ALTER TABLE password_settings DROP COLUMN alternative_password_change_link;
