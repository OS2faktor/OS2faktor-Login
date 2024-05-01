CREATE OR REPLACE VIEW view_hardware_token AS
  SELECT cmc.id,
         cmc.name,
         cmc.serialnumber,
         p.samaccount_name,
         p.name AS person_name,
         (p.locked_dataset OR p.locked_person OR p.locked_admin OR p.locked_dead OR p.locked_disenfranchised OR p.locked_password OR p.locked_expired) AS locked
  FROM cached_mfa_client cmc
  JOIN persons p ON p.id = cmc.person_id
  WHERE cmc.type = 'TOTPH'
  AND cmc.serialnumber IS NOT NULL;