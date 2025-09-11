create index idx_local_registered_mfa_clients_cpr on local_registered_mfa_clients (cpr);

alter table persons_attributes modify column attribute_value varchar(768) not null;
create index idx_persons_attributes_keyvalue on persons_attributes (attribute_key, attribute_value);
