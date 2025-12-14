DELETE ph FROM password_history ph
LEFT JOIN persons p ON ph.person_id = p.id
WHERE p.id IS NULL;

ALTER TABLE password_history
ADD CONSTRAINT fk_password_history_person
FOREIGN KEY (person_id) REFERENCES persons(id)
ON DELETE CASCADE;
