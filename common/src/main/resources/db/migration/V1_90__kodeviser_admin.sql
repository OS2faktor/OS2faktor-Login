ALTER TABLE persons ADD kodeviser_admin boolean DEFAULT false NOT NULL AFTER registrant;
ALTER TABLE persons_aud ADD kodeviser_admin boolean NULL AFTER registrant;