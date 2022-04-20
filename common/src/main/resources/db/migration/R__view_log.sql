CREATE OR REPLACE VIEW view_audit_log AS
  SELECT a.id, a.cpr, a.tts, a.person_name, COALESCE(p.samaccount_name, p.user_id) AS user_id, a.message, a.person_id, a.person_domain, a.log_action
  FROM auditlogs a
  LEFT JOIN persons p ON a.person_id = p.id
