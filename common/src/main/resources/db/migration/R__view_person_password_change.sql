CREATE OR REPLACE VIEW view_person_password_change AS
  SELECT p.id,
         p.samaccount_name,
         p.name,
         p.nsis_level,
         d.name AS `domain`,
         p.cpr
  FROM persons p
  JOIN domains d ON p.domain_id = d.id
  WHERE p.nsis_allowed = 1 AND
        nsis_level != 'NONE';