-- created 09.06.2025
-- can safely be removed from the code roughly 13 months later,
-- as our trigger will handle cleanup on new records
--
-- CALL SP_cleanup_auditlogs_details();

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_cleanup_auditlogs_details $$

CREATE PROCEDURE SP_cleanup_auditlogs_details() 
BEGIN

  SET @min_referenced_id = (
    SELECT COALESCE(auditlogs_details_id, 1)
    FROM auditlogs
    WHERE auditlogs_details_id IS NOT NULL
    ORDER BY id ASC
    LIMIT 1
  );

  DELETE FROM auditlogs_details WHERE id < @min_referenced_id;

END $$
DELIMITER ;
