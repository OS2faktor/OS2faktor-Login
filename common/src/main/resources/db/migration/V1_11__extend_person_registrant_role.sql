ALTER TABLE persons ADD COLUMN registrant BOOLEAN NOT NULL DEFAULT 0 AFTER supporter;
ALTER TABLE persons_aud ADD COLUMN registrant BOOLEAN NULL AFTER supporter;
