DELETE FROM email_template_children
WHERE email_template_id IN (
  SELECT id FROM email_templates
  WHERE template_type IN ('FULL_SERVICE_IDP_REMOVED', 'FULL_SERVICE_IDP_ASSIGNED')
);
