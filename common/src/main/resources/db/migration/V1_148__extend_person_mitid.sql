ALTER TABLE persons     ADD private_mit_id BOOLEAN NOT NULL DEFAULT 0 AFTER password_reset_admin;
ALTER TABLE persons_aud ADD private_mit_id BOOLEAN NULL AFTER password_reset_admin;