CREATE OR REPLACE VIEW view_person_admin_identities AS
SELECT 
    p.id,
    p.samaccount_name AS user_id,
    p.name,
    (p.locked_dataset <> 0 OR p.locked_person <> 0 OR p.locked_admin <> 0 OR 
     p.locked_dead <> 0 OR p.locked_disenfranchised <> 0 OR p.locked_password <> 0 OR 
     p.locked_expired <> 0) AS locked,
    p.locked_dataset,
    p.locked_person,
    p.locked_admin,
    (p.locked_dead <> 0 OR p.locked_disenfranchised <> 0) AS locked_civil_state,
    p.locked_expired,
    p.locked_password,
    p.nsis_allowed,
    p.nsis_level,
    CASE 
        WHEN d.parent_domain_id IS NULL THEN d.name
        ELSE CONCAT(pd.name, ' - ', d.name)
    END AS domain,
    CASE 
        WHEN COALESCE(mfa_count.client_count, 0) > 0 THEN 'Ja'
        ELSE 'Nej'
    END AS mfa_clients,
    CASE 
        WHEN p.approved_conditions = FALSE OR 
             p.approved_conditions_tts IS NULL OR 
             COALESCE((SELECT must_approve_tts FROM terms_and_conditions LIMIT 1), 0) > COALESCE(p.approved_conditions_tts, 0) THEN 'Nej'
        ELSE 'Ja'
    END AS approved_conditions,
    p.robot
FROM persons p
JOIN domains d ON p.domain_id = d.id
LEFT JOIN domains pd ON d.parent_domain_id = pd.id
LEFT JOIN (
    SELECT person_id, COUNT(*) as client_count
    FROM cached_mfa_client
    GROUP BY person_id
) mfa_count ON p.id = mfa_count.person_id;
