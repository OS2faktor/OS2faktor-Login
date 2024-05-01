ALTER TABLE persons ADD COLUMN cpr_name_updated BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE persons_aud ADD COLUMN cpr_name_updated BOOLEAN NULL;
