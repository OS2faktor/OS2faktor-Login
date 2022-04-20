CREATE OR REPLACE VIEW view_person_where_password_change_allowed AS
  SELECT p.id,
         COALESCE(p.samaccount_name, p.user_id) AS user_id,
         p.name,
         d.name AS domain_name,
         g.id AS required_group_id
  FROM persons p
  JOIN domains d ON p.domain_id = d.id
  JOIN password_settings ps ON ps.domain_id = d.id
  JOIN groups g on g.id = ps.change_password_on_users_group_id
  WHERE p.nsis_allowed = 0
    AND ps.change_password_on_users_enabled = 1;