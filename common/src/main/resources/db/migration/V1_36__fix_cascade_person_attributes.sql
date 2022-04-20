SELECT CONCAT(
  'ALTER TABLE `persons_attributes` DROP FOREIGN KEY `',
  constraint_name,
  '`'
) INTO @sqlst
  FROM information_schema.KEY_COLUMN_USAGE
  WHERE table_name = 'persons_attributes'
    AND referenced_table_name='persons'
    AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE persons_attributes ADD FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE;