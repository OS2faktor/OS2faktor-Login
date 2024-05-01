DELETE FROM ggroups WHERE domain_id = (SELECT id FROM domains WHERE name = 'OS2faktor');
DELETE FROM password_settings WHERE domain_id = (SELECT id FROM domains WHERE name = 'OS2faktor');
DELETE FROM persons WHERE domain_id = (SELECT id FROM domains WHERE name = 'OS2faktor');
DELETE FROM session_settings WHERE domain_id = (SELECT id FROM domains WHERE name = 'OS2faktor');
DELETE FROM domains WHERE name = 'OS2faktor';
