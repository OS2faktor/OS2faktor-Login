ALTER TABLE auditlogs MODIFY person_id BIGINT null;
ALTER TABLE auditlogs MODIFY person_name VARCHAR(255) null;
ALTER TABLE auditlogs MODIFY cpr VARCHAR(10) null;
ALTER TABLE auditlogs MODIFY person_domain  varchar(255) null;