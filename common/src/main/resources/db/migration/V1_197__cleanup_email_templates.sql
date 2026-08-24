-- use the new standard template instead
delete from email_template_children where email_template_id = (select id from email_templates where template_type = 'FULL_SERVICE_IDP_REMOVED');
