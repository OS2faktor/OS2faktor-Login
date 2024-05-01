ALTER TABLE persons ADD COLUMN student_password VARCHAR(255) NULL;
ALTER TABLE persons_aud ADD COLUMN student_password VARCHAR(255) NULL;

CREATE TABLE school_class_password_word (
   id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   school_class_id               BIGINT NOT NULL,
   word                          VARCHAR(32) NOT NULL,
   
   CONSTRAINT fk_school_class_password_word FOREIGN KEY (school_class_id) REFERENCES school_classes(id) ON DELETE CASCADE
);

CREATE TABLE school_class_password_word_aud (
   id                            BIGINT NOT NULL,
   rev                           BIGINT NOT NULL,
   revtype                       TINYINT,

   school_class_id               BIGINT,
   word                          VARCHAR(32),

   FOREIGN KEY fk_school_class_password_word_aud_rev (rev) REFERENCES revinfo(id),
   PRIMARY KEY (id, rev)
);