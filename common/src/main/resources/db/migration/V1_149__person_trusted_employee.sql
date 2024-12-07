ALTER TABLE persons     ADD trusted_employee BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE persons_aud ADD trusted_employee BOOLEAN NULL;

INSERT INTO domains (name) values ('Betroede medarbejdere');