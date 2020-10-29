CREATE OR REPLACE VIEW view_audit_log AS
  SELECT a.id, a.cpr, a.tts, a.person_name, p.user_id, a.message, p.samaccount_name, a.person_id
  FROM auditlogs a
  LEFT JOIN persons p ON a.person_id = p.id
