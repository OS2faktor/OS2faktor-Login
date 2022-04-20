ALTER TABLE password_settings DROP COLUMN cache_ad_password_interval;
ALTER TABLE persons DROP COLUMN ad_password;
ALTER TABLE persons DROP COLUMN ad_password_timestamp;
ALTER TABLE persons_aud DROP COLUMN ad_password;
ALTER TABLE persons_aud DROP COLUMN ad_password_timestamp;