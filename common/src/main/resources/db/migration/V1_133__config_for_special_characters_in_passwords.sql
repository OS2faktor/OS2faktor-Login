ALTER TABLE password_settings ADD specific_special_characters_enabled BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE password_settings ADD allowed_special_characters VARCHAR(255);