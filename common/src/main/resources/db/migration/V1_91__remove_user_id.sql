DELETE FROM persons WHERE samaccount_name IS NULL;
ALTER TABLE persons MODIFY COLUMN samaccount_name VARCHAR(255) NOT NULL; 
ALTER TABLE persons DROP COLUMN user_id;
ALTER TABLE persons_aud DROP COLUMN user_id;