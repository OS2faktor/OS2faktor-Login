-- groups is a reserved keyword in MySQL, so local development is hard if we use that word
ALTER TABLE `groups` RENAME TO ggroups;