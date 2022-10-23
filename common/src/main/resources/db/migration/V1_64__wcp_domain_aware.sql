ALTER TABLE windows_credential_provider_clients ADD COLUMN domain_id BIGINT NULL;
ALTER TABLE windows_credential_provider_clients ADD CONSTRAINT fk_windows_credential_provider_clients_domains FOREIGN KEY (domain_id) REFERENCES domains(id);
UPDATE windows_credential_provider_clients SET domain_id = (SELECT MIN(id) from domains WHERE name != 'OS2faktor');
ALTER TABLE windows_credential_provider_clients MODIFY COLUMN domain_id BIGINT NOT NULL;
