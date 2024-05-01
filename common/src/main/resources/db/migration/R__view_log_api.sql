CREATE OR REPLACE VIEW view_audit_log_api AS
  SELECT a.id, a.tts, a.ip_address, a.correlation_id, a.person_id, a.person_name, a.cpr, a.performer_id, a.performer_name,
         a.log_action, a.message, a.person_domain, p.samaccount_name, ad.detail_type, ad.detail_content, ad.detail_supplement
  FROM auditlogs a
  LEFT JOIN persons p ON a.person_id = p.id
  LEFT JOIN auditlogs_details ad on ad.id = a.auditlogs_details_id;