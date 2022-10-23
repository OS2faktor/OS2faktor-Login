package dk.digitalidentity.common.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.dao.PersonAttributeDao;
import dk.digitalidentity.common.dao.model.PersonAttribute;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;

@Service
public class PersonAttributeService {
	
	@Autowired
	private PersonAttributeDao personAttributeSetDao;

	@Autowired
	private PersonService personService;

	@Autowired
	private SqlServiceProviderConfigurationService sqlServiceProviderConfigurationService;

	@Transactional(rollbackFor = Exception.class)
	public void aggregateAttributes() {

		// Get set of attributes currently assigned to any person
		Set<String> computedListOfAttributes = personService.findDistinctAttributeNames();

		// Get set of person attributes used in SP Claims
		Set<String> personAttributesUsedInSPs = new HashSet<>();
		for (SqlServiceProviderConfiguration spConfig : sqlServiceProviderConfigurationService.getAll()) {
			Set<SqlServiceProviderRequiredField> requiredFields = spConfig.getRequiredFields();

			personAttributesUsedInSPs.add(spConfig.getNameIdValue());

			for (SqlServiceProviderRequiredField requiredField : requiredFields) {
				personAttributesUsedInSPs.add(requiredField.getPersonField());
			}
		}

		List<PersonAttribute> savedAttributes = personAttributeSetDao.findAll();

		// If no ServiceProvider is currently using a person Attribute in a claim
		// AND no person currently has that attribute, then delete it
		Set<PersonAttribute> toBeDeleted = new HashSet<>();
		for (PersonAttribute savedAttribute : savedAttributes) {
			if (!personAttributesUsedInSPs.contains(savedAttribute.getName()) && !computedListOfAttributes.contains(savedAttribute.getName())) {
				toBeDeleted.add(savedAttribute);
			}
		}
		personAttributeSetDao.deleteAll(toBeDeleted);

		computedListOfAttributes.removeAll(savedAttributes.stream().map(PersonAttribute::getName).collect(Collectors.toSet()));
		Set<PersonAttribute> toBeAdded = computedListOfAttributes.stream().map(PersonAttribute::new).collect(Collectors.toSet());
		
		personAttributeSetDao.saveAll(toBeAdded);
	}

	public PersonAttribute getById(long id) {
		return personAttributeSetDao.findById(id);
	}
	
	public PersonAttribute getByName(String name) {
		return personAttributeSetDao.findByName(name);
	}

	public List<PersonAttribute> getAll() {
		return personAttributeSetDao.findAll();
	}

	public PersonAttribute save(PersonAttribute entity) {
		return personAttributeSetDao.save(entity);
	}

	public List<PersonAttribute> saveAll(List<PersonAttribute> entities) {
		return personAttributeSetDao.saveAll(entities);
	}

	public void delete(PersonAttribute personAttribute) {
		personAttributeSetDao.delete(personAttribute);
	}

	public void deleteAll(List<PersonAttribute> entities) {
		personAttributeSetDao.deleteAll(entities);
	}
}