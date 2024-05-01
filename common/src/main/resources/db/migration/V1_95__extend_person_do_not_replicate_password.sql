ALTER TABLE persons     ADD COLUMN do_not_replicate_password BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE persons_aud ADD COLUMN do_not_replicate_password BOOLEAN;