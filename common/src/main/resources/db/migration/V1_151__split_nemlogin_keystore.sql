INSERT INTO keystores (subject_dn, expires, keystore, password, last_updated, disabled, alias, kms, certificate, kms_alias)
SELECT subject_dn, expires, keystore, password, CURRENT_TIMESTAMP, disabled, 'NEMLOGIN', kms, certificate, kms_alias
FROM keystores WHERE alias = 'OCES';
