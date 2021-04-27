package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.service.DomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.api.dto.CoreData;
import dk.digitalidentity.api.dto.CoreDataDelete;
import dk.digitalidentity.api.dto.CoreDataDeleteEntry;
import dk.digitalidentity.api.dto.CoreDataEntry;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CoreDataService {

	@Autowired
	private PersonService personService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private AuditLogger auditLogger;

	@Transactional(rollbackFor = Exception.class)
	public void load(CoreData coreData, boolean fullLoad) throws IllegalArgumentException {
		List<Person> updatedPersons = new ArrayList<>();
		List<Person> createdPersons = new ArrayList<>();

		Domain domain = domainService.getByName(coreData.getDomain());

		// Get people from the database
		List<Person> personList = personService.getByDomain(coreData.getDomain());

		// Validate input
		String result = validateInput(coreData, personList, fullLoad);
		if (result != null) {
			throw new IllegalArgumentException(result);
		}

		// Go through the received list of entries, check if there's a matching person object, then update/create
		Map<String, Person> personMap = personList.stream().collect(Collectors.toMap(Person::getIdentifier, Function.identity()));
		for (CoreDataEntry coreDataEntry : coreData.getEntryList()) {
			Person person = personMap.get(coreDataEntry.getIdentifier());
			if (person != null) {
				boolean modified = updatePerson(coreDataEntry, person);

				if (modified) {
					updatedPersons.add(person);
				}
			}
			else {
				person = createPerson(coreDataEntry, domain);
				createdPersons.add(person);
			}
		}

		// If we're doing a full load, go through list of person objects, lock any person that does not have a matching entry
		if (fullLoad) {
			Map<String, CoreDataEntry> entryMap = coredataToMap(coreData);
			List<Person> removedFromDataset = new ArrayList<>();

			for (Person person : personList) {
				CoreDataEntry entry = entryMap.get(person.getIdentifier());

				if (entry == null && !person.isLockedDataset()) {
					person.setLockedDataset(true);
					removedFromDataset.add(person);
				}
			}

			updatedPersons.addAll(removedFromDataset);
			auditLogger.removedAllFromDataset(removedFromDataset);
		}

		// Save all the updated people
		if (updatedPersons.size() > 0) {
			personService.saveAll(updatedPersons);
			log.info(updatedPersons.size() + " persons were updated");
		}

		// Save all the created people
		if (createdPersons.size() > 0) {
			createdPersons = personService.saveAll(createdPersons);

			auditLogger.addedAllToDataset(createdPersons);
			log.info(createdPersons.size() + " persons were created");
		}
	}

	public CoreData getByDomain(String domain) throws IllegalArgumentException {
		if (StringUtils.isEmpty(domain)) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}

		// Create response object
		CoreData coreData = new CoreData();
		coreData.setDomain(domain);

		// Add matching people
		List<Person> byDomainAndCpr = personService.getByDomain(domain);
		if (byDomainAndCpr == null) {
			return coreData;
		}
		
		List<CoreDataEntry> matches = byDomainAndCpr.stream().map(CoreDataEntry::new).collect(Collectors.toList());
		coreData.setEntryList(matches);

		return coreData;
	}

	public CoreData getByDomainAndCpr(Domain domain, String cpr) throws IllegalArgumentException {
		if (domain == null) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}

		if (StringUtils.isEmpty(cpr)) {
			throw new IllegalArgumentException("Cpr cannot be empty");
		}

		// Create response object
		CoreData coreData = new CoreData();
		coreData.setDomain(domain.getName());

		// Add matching people
		List<Person> byDomainAndCpr = personService.getByDomainAndCpr(domain, cpr);
		if (byDomainAndCpr == null) {
			return coreData;
		}
		
		List<CoreDataEntry> matches = byDomainAndCpr.stream().map(CoreDataEntry::new).collect(Collectors.toList());
		coreData.setEntryList(matches);

		return coreData;
	}

	@Transactional(rollbackFor = Exception.class)
	public void lockDataset(CoreDataDelete coreDataDelete) throws IllegalArgumentException {
		if (StringUtils.isEmpty(coreDataDelete.getDomain())) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}
		
		Domain domain = domainService.getByName(coreDataDelete.getDomain());
		if (domain == null) {
			throw new IllegalArgumentException("Unknown domain: " + coreDataDelete.getDomain());
		}

		if (coreDataDelete.getEntryList() == null) {
			throw new IllegalArgumentException("entryList cannot be null");
		}

		List<Person> toSave = new ArrayList<Person>();
		for (CoreDataDeleteEntry entry : coreDataDelete.getEntryList()) {
			List<Person> matches = personService.getByDomainAndCpr(domain, entry.getCpr());
			if (matches != null) {
				for (Person match : matches) {
					if (Objects.equals(match.getSamaccountName(), entry.getSamAccountName()) &&
						Objects.equals(match.getUuid(), entry.getUuid())) {

						match.setLockedDataset(true);						
						toSave.add(match);
					}
				}
			}
		}
		
		personService.saveAll(toSave);
		auditLogger.removedAllFromDataset(toSave);
	}

	private Map<String, CoreDataEntry> coredataToMap(CoreData coreData) {
		Map<String, CoreDataEntry> coreDataMap = new HashMap<>();
		
		for (CoreDataEntry coreDataEntry : coreData.getEntryList()) {
			coreDataMap.put(coreDataEntry.getIdentifier(), coreDataEntry);
		}

		return coreDataMap;
	}

	private String validateInput(CoreData coreData, List<Person> databasePeople, boolean fullLoad) {
		List<CoreDataEntry> entries = coreData.getEntryList();

		if (entries == null || entries.size() == 0) {
			return "Tom entryList for domæne " + coreData.getDomain();
		}

		Optional<Domain> anyMatch = domainService.getAll()
				.stream()
				.filter(domain -> Objects.equals(domain.getName(), coreData.getDomain()))
				.findAny();

		if (anyMatch.isEmpty()) {
			return "Angivne domæne (" + coreData.getDomain() + ") findes ikke i os2faktor login";
		}

		// convert "" to null for more correct handling
		entries.forEach(e -> {
			if (StringUtils.isEmpty(e.getSamAccountName())) {
				e.setSamAccountName(null);
			}
		});
		
		// Check for duplicate SAMAccountNames
		Set<String> existingSAMAccountNames = new HashSet<>();
		for (CoreDataEntry entry : entries) {
			if (!StringUtils.isEmpty(entry.getSamAccountName())) {
				if (!existingSAMAccountNames.contains(entry.getSamAccountName())) {
					existingSAMAccountNames.add(entry.getSamAccountName());
				}
				else {
					return "Flere personer med tilknyttet AD konto (" + entry.getSamAccountName() + ") for domæne " + coreData.getDomain();
				}
			}
		}
		
		// Map people from database to a list of matching SAMAccountNames, ignore null or empty
		HashMap<String, List<Person>> listOfMatches = new HashMap<>();
		for (Person person : databasePeople) {
			if (!StringUtils.isEmpty(person.getSamaccountName())) {
				List<Person> people = listOfMatches.get(person.getSamaccountName());

				if (people == null) {
					people = new ArrayList<>();
				}
				people.add(person);

				listOfMatches.put(person.getSamaccountName(), people);
			}
		}

		for (CoreDataEntry entry : entries) {
			entry.setDomain(coreData.getDomain());

			if (entry.getUuid() == null || entry.getUuid().length() != 36) {
				return "Person med ugyldigt UUID: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
			else if (StringUtils.isEmpty(entry.getName())) {
				return "Person uden navn: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
			else if (StringUtils.isEmpty(entry.getCpr())) {
				return "Person uden CPR nummer: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
			else if (entry.getCpr().length() != 10) {
				return "Person med ugyldigt CPR nummer: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}

			// we cannot allow multiple persons (within a given domain) to share a linked sAMAccountName, so we check
			// if one of the existing persons in the database has the same sAMAccountName (who are different from
			// the person record in the CoreData payload), and then either reject the payload entirely, or perform
			// a removal of the person, according to the following rules
			//
			// * if we are performing a full sync, we can safely ignore the validation error, because we previously
			//   validated that the full payload does not contain duplicates, so the update will correct any issues
			// * if the existing record has previously been locked by the CoreData API, then we can safely remove
			//   the person
			// * otherwise reject update

			if (!fullLoad && !StringUtils.isEmpty(entry.getSamAccountName())) {
				List<Person> dbMatches = listOfMatches.get(entry.getSamAccountName());

				if (dbMatches != null && dbMatches.size() > 0) {
					for (Person match : dbMatches) {
						if (!CoreDataEntry.compare(match, entry)) {
							if (match.isLockedDataset()) {
								log.warn("Had to delete " + match.getUuid() + "/" + match.getSamaccountName() + " for domain " + match.getDomain().getName() + " because sAMAccountName was moved to different person: " + match.getSamaccountName());
								personService.delete(match, null);

								// ensure that we do not perform updates on this later in the flow
								databasePeople.removeIf(p -> p.getId() == match.getId());
							}
							else {
								return "Aktiv person med tilknyttet AD konto (" + entry.getSamAccountName() + ") som anden person også har: " + entry.getUuid();
							}
						}
					}
				}
			}
		}

		return null;
	}

	private Person createPerson(CoreDataEntry coreDataEntry, Domain domain) {
		Person person = new Person();
		person.setSamaccountName(coreDataEntry.getSamAccountName());
		person.setDomain(domain);
		person.setApprovedConditions(false);
		person.setApprovedConditionsTts(null);
		person.setCpr(coreDataEntry.getCpr());
		person.setEmail(coreDataEntry.getEmail());
		person.setLockedAdmin(false);
		person.setLockedDataset(false);
		person.setLockedPerson(false);
		person.setName(coreDataEntry.getName());
		person.setNameAlias(coreDataEntry.getName());
		person.setNsisLevel(NSISLevel.NONE);
		person.setNsisAllowed(coreDataEntry.isNsisAllowed());
		person.setUuid(coreDataEntry.getUuid());
		person.setAttributes(coreDataEntry.getAttributes());

		return person;
	}

	private boolean updatePerson(CoreDataEntry coreDataEntry, Person person) {
		boolean modified = false;

		if (!Objects.equals(person.getNameAlias(), coreDataEntry.getName())) {
			person.setNameAlias(coreDataEntry.getName());
			modified = true;
		}

		if (!Objects.equals(person.getEmail(), coreDataEntry.getEmail())) {
			person.setEmail(StringUtils.isEmpty(coreDataEntry.getEmail()) ? null : coreDataEntry.getEmail());
			modified = true;
		}

		if (person.isLockedDataset()) {
			person.setLockedDataset(false);
			person.setApprovedConditions(false);
			person.setApprovedConditionsTts(null);
			person.setNsisLevel(NSISLevel.NONE);
			person.setNsisPassword(null);

			auditLogger.addedToDataset(person);
			modified = true;
		}

		if (coreDataEntry.isNsisAllowed() != person.isNsisAllowed()) {
			if (coreDataEntry.isNsisAllowed()) {
				person.setNsisAllowed(true);
				person.setNsisPassword(null);
			}
			else {
				person.setNsisAllowed(false);
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisPassword(null);
			}

			modified = true;
		}

		// map equality works on element equality (which works because they are Strings), and the order is not relevant (equality still works)
		if (!Objects.equals(person.getAttributes(), coreDataEntry.getAttributes())) {
			person.setAttributes(coreDataEntry.getAttributes());
			modified = true;
		}

		return modified;
	}
}
