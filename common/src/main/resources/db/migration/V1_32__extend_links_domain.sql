-- copy existing links to all domains
ALTER TABLE links ADD COLUMN domain_id BIGINT NULL;
ALTER TABLE links ADD CONSTRAINT fk_links_domains FOREIGN KEY (domain_id) REFERENCES domains(id);
INSERT INTO links (link_text, link, domain_id)
       SELECT link_text, link, d.id FROM links l JOIN domains d;
DELETE FROM links WHERE domain_id IS NULL;
ALTER TABLE links MODIFY COLUMN domain_id BIGINT NOT NULL;