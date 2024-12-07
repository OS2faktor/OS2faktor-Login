-- cleanup, so we only have 1 certificate
DELETE FROM keystores WHERE primary_for_idp = 0;

-- add alias and remove the primary flags (alias will control everything fro now on)
ALTER TABLE keystores ADD COLUMN alias VARCHAR(255) NOT NULL DEFAULT 'OCES';
ALTER TABLE keystores DROP COLUMN primary_for_idp;
ALTER TABLE keystores DROP COLUMN primary_for_nem_login;

-- flag to indicate it is stored in KMS
ALTER TABLE keystores ADD COLUMN kms BOOLEAN NOT NULL DEFAULT 0;

-- keystore and password can now be null
ALTER TABLE keystores MODIFY COLUMN keystore BLOB NULL;
ALTER TABLE keystores MODIFY COLUMN password VARCHAR(64) NULL;

-- but we can optionally store a certificate and an KMS alias
ALTER TABLE keystores ADD COLUMN certificate BLOB NULL;
ALTER TABLE keystores ADD COLUMN kms_alias VARCHAR(255) NULL;
