ALTER TABLE persons ADD qualified_signature BOOLEAN DEFAULT 0 NOT NULL AFTER private_mit_id;
ALTER TABLE persons_aud ADD qualified_signature BOOLEAN NULL AFTER private_mit_id;