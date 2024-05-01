ALTER TABLE terms_and_conditions ADD COLUMN last_updated_tts DATETIME DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE privacy_policy ADD COLUMN last_updated_tts DATETIME DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tu_terms_and_conditions ADD COLUMN last_updated_tts DATETIME DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE tu_terms_and_conditions_aud ADD COLUMN last_updated_tts DATETIME NULL;
ALTER TABLE terms_and_conditions_aud ADD COLUMN last_updated_tts DATETIME NULL;
ALTER TABLE privacy_policy_aud ADD COLUMN last_updated_tts DATETIME NULL;