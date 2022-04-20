ALTER TABLE domains
ADD COLUMN      parent_domain_id    BIGINT NULL,
ADD CONSTRAINT  fk_domains_domains  FOREIGN KEY (parent_domain_id) REFERENCES domains(id);

ALTER TABLE domains_aud
ADD COLUMN      parent_domain_id    BIGINT  NULL;
