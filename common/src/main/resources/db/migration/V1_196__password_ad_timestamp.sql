ALTER TABLE persons ADD COLUMN password_ad_timestamp DATETIME NULL AFTER password_timestamp;
ALTER TABLE persons_aud ADD COLUMN password_ad_timestamp DATETIME NULL AFTER password_timestamp;
