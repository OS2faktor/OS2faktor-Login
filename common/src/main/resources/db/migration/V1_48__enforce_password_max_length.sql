ALTER TABLE password_settings ADD COLUMN max_length BIGINT NOT NULL DEFAULT 64 AFTER min_length;
