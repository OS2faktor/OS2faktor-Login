ALTER TABLE persons     ADD COLUMN bad_password_leak_check_tts DATE NULL;
ALTER TABLE persons     ADD COLUMN bad_password BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE persons     ADD COLUMN bad_password_reason VARCHAR(64) NULL;
ALTER TABLE persons     ADD COLUMN bad_password_deadline_tts DATE;

ALTER TABLE persons_aud ADD COLUMN bad_password BOOLEAN NULL;
ALTER TABLE persons_aud ADD COLUMN bad_password_reason VARCHAR(64) NULL;
ALTER TABLE persons_aud ADD COLUMN bad_password_deadline_tts DATE NULL;
