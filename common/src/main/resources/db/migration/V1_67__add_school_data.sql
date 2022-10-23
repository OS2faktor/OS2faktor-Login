CREATE TABLE school_roles (
	id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  	person_id                    BIGINT NOT NULL,
  	institution_id               VARCHAR(255) NOT NULL,
  	institution_name             VARCHAR(255),
  	role                         VARCHAR(255) NOT NULL,
  	
  	CONSTRAINT fk_school_role_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE school_roles_aud (
    id                             BIGINT NOT NULL,
    rev                            BIGINT NOT NULL,
    revtype                        TINYINT,

    person_id                      BIGINT,
    institution_id                 VARCHAR(255),
    institution_name               VARCHAR(255),
    role                           VARCHAR(255),

    FOREIGN KEY fk_school_roles_aud_rev (rev) REFERENCES revinfo(id),
    PRIMARY KEY (id, rev)
);

CREATE TABLE school_classes (
	id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	name                         VARCHAR(255) NOT NULL,
	level                        VARCHAR(255) NULL,
	type                         VARCHAR(255) NOT NULL,
  	institution_id               VARCHAR(255) NOT NULL,
  	class_identifier             VARCHAR(255) NOT NULL
);

CREATE TABLE school_classes_aud (
    id                             BIGINT NOT NULL,
    rev                            BIGINT NOT NULL,
    revtype                        TINYINT,

    name                           VARCHAR(255),
    level                          VARCHAR(255),
    type                           VARCHAR(255),
    institution_id                 VARCHAR(255),
    class_identifier               VARCHAR(255),

    FOREIGN KEY fk_school_classes_aud_rev (rev) REFERENCES revinfo(id),
    PRIMARY KEY (id, rev)
);

CREATE TABLE school_roles_school_classes (
	id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	school_role_id               BIGINT NOT NULL,
  	school_class_id              BIGINT NOT NULL,
  	
  	CONSTRAINT fk_school_roles_school_classes_role FOREIGN KEY (school_role_id) REFERENCES school_roles(id) ON DELETE CASCADE,
  	CONSTRAINT fk_school_roles_school_classes_class FOREIGN KEY (school_class_id) REFERENCES school_classes(id) ON DELETE CASCADE
);

CREATE TABLE school_roles_school_classes_aud (
    id                             BIGINT NOT NULL,
    rev                            BIGINT NOT NULL,
    revtype                        TINYINT,

    school_role_id                 BIGINT,
    school_class_id                BIGINT,

    FOREIGN KEY fk_school_roles_school_classes_aud_rev (rev) REFERENCES revinfo(id),
    PRIMARY KEY (id, rev)
);