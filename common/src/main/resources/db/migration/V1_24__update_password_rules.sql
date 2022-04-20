ALTER TABLE password_settings ADD COLUMN lowercase_letters TINYINT(1) DEFAULT 1;
ALTER TABLE password_settings ADD COLUMN uppercase_letters TINYINT(1) DEFAULT 0;
ALTER TABLE password_settings ADD COLUMN complex_password TINYINT(1) DEFAULT 0;
UPDATE password_settings SET uppercase_letters = 1 WHERE capital_and_small_letters = 1;
ALTER TABLE password_settings DROP COLUMN capital_and_small_letters;