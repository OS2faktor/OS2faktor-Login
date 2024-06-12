DELETE FROM person_statistics WHERE person_id IN (SELECT person_id FROM person_statistics GROUP BY person_id HAVING COUNT(*) > 1);

ALTER TABLE person_statistics MODIFY COLUMN person_id BIGINT NOT NULL UNIQUE;