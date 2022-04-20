CREATE OR REPLACE VIEW view_person_admin_identities AS
  SELECT p.id,
         COALESCE(p.samaccount_name, p.user_id) AS user_id,
         p.name,
         (p.locked_dataset or p.locked_person or p.locked_admin or p.locked_password or p.locked_dead) AS locked,
         p.locked_dataset,
         p.locked_person,
         p.locked_admin,
         p.locked_password,
         p.locked_dead,
         p.nsis_level,
         IF(d.parent_domain_id IS NULL, d.name, CONCAT(pd.name, " - ", d.name)) `domain`
  FROM persons p
  JOIN domains d ON p.domain_id = d.id
  LEFT JOIN domains AS pd ON d.parent_domain_id = pd.id;
