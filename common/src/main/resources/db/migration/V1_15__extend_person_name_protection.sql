ALTER TABLE persons ADD COLUMN name_protected BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE persons_aud ADD COLUMN name_protected BOOLEAN NULL;
ALTER TABLE persons ADD COLUMN name_alias VARCHAR(255) NULL;
ALTER TABLE persons_aud ADD COLUMN name_alias VARCHAR(255) NULL;