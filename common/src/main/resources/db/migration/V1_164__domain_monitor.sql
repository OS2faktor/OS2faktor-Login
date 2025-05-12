ALTER TABLE domains DROP COLUMN monitored;
UPDATE domains SET standalone = 1 WHERE name = 'Betroede medarbejdere';