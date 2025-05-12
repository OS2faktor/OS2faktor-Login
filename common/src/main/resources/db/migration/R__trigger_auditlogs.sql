DROP TRIGGER IF EXISTS reverse_cascade_auditlogs;
DELIMITER $$
CREATE TRIGGER reverse_cascade_auditlogs
  AFTER DELETE
  ON auditlogs FOR EACH ROW
  BEGIN
    DELETE FROM auditlogs_details WHERE id = old.auditlogs_details_id;
  END$$
DELIMITER ;
