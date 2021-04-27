CREATE OR REPLACE VIEW view_person_pre_registered_identities AS
  SELECT p.id,
         p.samaccount_name,
         p.name,
         p.nsis_level,
         p.approved_conditions,
         d.name AS `domain`,
         p.cpr
  FROM persons p
  JOIN domains d ON p.domain_id = d.id
  WHERE p.nsis_allowed = 1 AND
        p.nsis_password IS NULL AND
        p.user_id IS NULL AND
        p.nsis_level = 'NONE';
