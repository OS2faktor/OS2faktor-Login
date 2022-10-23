ALTER TABLE persons ADD COLUMN expire_timestamp DATETIME NULL;
ALTER TABLE persons_aud ADD COLUMN expire_timestamp DATETIME NULL;

ALTER TABLE persons ADD COLUMN locked_expired BOOLEAN NOT NULL DEFAULT 0 AFTER locked_disenfranchised;
ALTER TABLE persons_aud ADD COLUMN locked_expired BOOLEAN NULL AFTER locked_disenfranchised;