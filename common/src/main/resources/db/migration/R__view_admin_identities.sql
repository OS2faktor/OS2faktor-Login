CREATE OR REPLACE VIEW view_person_admin_identities AS
  SELECT p.id,
         COALESCE(p.samaccount_name, p.user_id) AS user_id,
         p.name,
         (p.locked_dataset OR p.locked_person OR p.locked_admin OR p.locked_dead OR p.locked_disenfranchised OR p.locked_password OR p.locked_expired) AS locked,
         p.locked_dataset,
         p.locked_person,
         p.locked_admin,
         (p.locked_dead OR p.locked_disenfranchised) AS locked_civil_state,
         p.locked_expired,
         p.locked_password,
         p.nsis_allowed,
         p.nsis_level,
         IF (d.parent_domain_id IS NULL, d.name, CONCAT(pd.name, " - ", d.name)) `domain`,
         IF (COUNT(c.person_id) > 0, 'Ja', 'Nej') AS mfa_clients,
         IF (p.approved_conditions IS FALSE OR p.approved_conditions_tts IS NULL OR (SELECT must_approve_tts FROM terms_and_conditions LIMIT 1) > p.approved_conditions_tts, 'Nej', 'Ja' ) AS approved_conditions
  FROM persons p
  JOIN domains d ON p.domain_id = d.id
  LEFT JOIN domains AS pd ON d.parent_domain_id = pd.id
  LEFT JOIN cached_mfa_client c ON (p.id = c.person_id)
  GROUP BY p.id;
