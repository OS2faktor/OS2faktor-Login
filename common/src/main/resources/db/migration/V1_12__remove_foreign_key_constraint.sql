SELECT CONCAT(
	'ALTER TABLE `auditlogs` DROP FOREIGN KEY `',
	constraint_name,
	'`'
) INTO @sqlst
	FROM information_schema.KEY_COLUMN_USAGE
	WHERE table_name = 'auditlogs'
		AND referenced_table_name='persons'
		AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;