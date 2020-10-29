CREATE OR REPLACE VIEW view_person_admin_identities AS
  SELECT p.id,
         p.user_id,
         p.samaccount_name,
         p.name,
         (p.locked_dataset || p.locked_person || p.locked_admin || p.locked_password) AS locked,
         p.locked_dataset,
         p.locked_person,
         p.locked_admin,
         p.locked_password,
         p.nsis_level,
         p.domain
  FROM persons p