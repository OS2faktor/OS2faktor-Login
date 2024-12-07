ALTER TABLE persons     ADD COLUMN external_nemlogin_user_uuid VARCHAR(36) NULL;
ALTER TABLE persons_aud ADD COLUMN external_nemlogin_user_uuid VARCHAR(36) NULL;
