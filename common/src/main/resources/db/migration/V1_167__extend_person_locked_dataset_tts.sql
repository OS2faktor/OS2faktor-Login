ALTER TABLE persons ADD COLUMN locked_dataset_tts DATETIME NULL AFTER locked_dataset;
ALTER TABLE persons_aud ADD COLUMN locked_dataset_tts DATETIME NULL AFTER locked_dataset;
