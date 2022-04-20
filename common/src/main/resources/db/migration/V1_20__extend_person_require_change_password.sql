ALTER TABLE persons ADD COLUMN force_change_password BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE persons_aud ADD COLUMN force_change_password BOOLEAN NULL;