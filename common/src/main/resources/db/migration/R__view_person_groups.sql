CREATE OR REPLACE VIEW view_persons_groups AS
  SELECT pg.id,
         pg.group_id,
         pg.person_id
  FROM persons_groups pg
  JOIN view_person_admin_identities p ON pg.person_id = p.id;
