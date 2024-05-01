ALTER TABLE email_template_children DROP COLUMN auditlogged;
ALTER TABLE message_queue DROP CONSTRAINT fk_message_queue_email_template;
ALTER TABLE message_queue DROP COLUMN email_template_id;
