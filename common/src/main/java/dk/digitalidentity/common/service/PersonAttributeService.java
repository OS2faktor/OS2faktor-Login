package dk.digitalidentity.common.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

	public void aggregateAttributes() {

		// Get set of attributes currently assigned to any person
		Set<String> computedListOfAttributes = personService.findDistinctAttributeNames();

		// Get set of person attributes used in SP Claims
		Set<String> personAttributesUsedInSPs = new HashSet<>();

		List<SqlServiceProviderConfiguration> spConfigs = sqlServiceProviderConfigurationService.getAll(sp -> {
			sp.getRequiredFields().size();
		});

		for (SqlServiceProviderConfiguration spConfig : spConfigs) {
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
		
		// only need entries, so no transaction needed
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
	
	public Map<String, String> getAttributeValueMappings(boolean includeStatic) {
		Map<String, String> result = new HashMap<>();

		List<PersonAttribute> allPersonAttributes = getAll();
		if (allPersonAttributes != null && !allPersonAttributes.isEmpty()) {
			allPersonAttributes.forEach(personAttribute -> result.put(personAttribute.getName(), (personAttribute.getDisplayName() != null) ? personAttribute.getDisplayName() : personAttribute.getName()));
		}

		if (includeStatic) {
			result.put("userId", "Brugernavn");
			result.put("uuid", "UUID");
			result.put("cpr", "Personnummer");
			result.put("email", "E-mail");
			result.put("name", "Navn");
			result.put("firstname", "Fornavn");
			result.put("lastname", "Efternavn");
			result.put("alias", "Kaldenavn");
			result.put("aliasFirstname", "KaldeFornavn");
			result.put("aliasLastname", "KaldeEfternavn");
		}

		return result.entrySet()
		    .stream()
		    .sorted(Map.Entry.comparingByValue())
		    .collect(Collectors.toMap(
		        Map.Entry::getKey,
		        Map.Entry::getValue,
		        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
	}
}