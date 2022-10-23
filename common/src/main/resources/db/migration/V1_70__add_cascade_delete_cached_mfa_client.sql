SELECT CONCAT(
 'ALTER TABLE `cached_mfa_client` DROP FOREIGN KEY `',
 constraint_name,
 '`'
) INTO @sqlst
 FROM information_schema.KEY_COLUMN_USAGE
 WHERE table_name = 'cached_mfa_client'
  AND referenced_table_name='persons'
  AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE cached_mfa_client
 ADD CONSTRAINT
 FOREIGN KEY (person_id)
 REFERENCES persons(id)
 ON DELETE CASCADE;