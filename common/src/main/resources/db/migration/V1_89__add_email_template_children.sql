CREATE TABLE email_template_children (
   id                             BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   email_template_id              BIGINT NOT NULL,
   domain_id                      BIGINT NOT NULL,
   title                          VARCHAR(255) NOT NULL,
   message                        MEDIUMTEXT NOT NULL,
   enabled                        BOOLEAN NOT NULL DEFAULT FALSE,
   eboks                          BOOLEAN NOT NULL DEFAULT FALSE,
   email                          BOOLEAN NOT NULL DEFAULT FALSE,

   CONSTRAINT fk_email_template_children_email_template FOREIGN KEY (email_template_id) REFERENCES email_templates(id) ON DELETE CASCADE,
   CONSTRAINT fk_email_template_children_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);

INSERT INTO email_template_children (email_template_id, domain_id, title, message, enabled, eboks, email)
 SELECT e.id, d.id, e.title, e.message, e.enabled, e.eboks, e.email FROM email_templates e, domains d;

ALTER TABLE email_templates DROP COLUMN title;
ALTER TABLE email_templates DROP COLUMN message;
ALTER TABLE email_templates DROP COLUMN enabled;
ALTER TABLE email_templates DROP COLUMN eboks;
ALTER TABLE email_templates DROP COLUMN email;
