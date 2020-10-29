package dk.digitalidentity.service;

import dk.digitalidentity.api.dto.CoreData;
import dk.digitalidentity.api.dto.CoreDataEntry;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class CoreDataService {

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	public ResponseEntity<String> load(CoreData coreData, boolean fullLoad) {
		List<Person> updatedPersons = new ArrayList<>();
		List<Person> createdPersons = new ArrayList<>();

		// Validate input
		String result = validateInput(coreData, fullLoad);
		if (result != null) {
			return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
		}

		// Get people from the database
		List<Person> personList = personService.getByDomain(coreData.getDomain());
		Map<String, Person> personMap = personList.stream().collect(Collectors.toMap(Person::getIdentifier, Function.identity()));

		// Get people from the received data
		List<CoreDataEntry> entryList = coreData.getEntryList();
		Map<String, CoreDataEntry> entryMap = coredataToMap(coreData);

		// Go through the received list of entries, check if theres a matching person object, then update/create
		for (CoreDataEntry coreDataEntry : entryList) {
			String entryID = Stream
					.of(coreData.getDomain(), coreDataEntry.getUuid(), coreDataEntry.getCpr(), coreDataEntry.getSamAccountName())
					.filter(str -> !StringUtils.isEmpty(str))
					.collect(Collectors.joining(":"));

			Person person = personMap.get(entryID);
			if (person != null) {
				boolean modified = updatePerson(coreDataEntry, person, coreData.getDomain());

				if (modified) {
					updatedPersons.add(person);
				}
			}
			else {
				person = createPerson(coreDataEntry, coreData.getDomain());
				createdPersons.add(person);
			}
		}

		// If we're doing a full load, go through list of person objects, lock any person that does not have a matching entry
		if (fullLoad) {
			for (Person person : personList) {
				CoreDataEntry entry = entryMap.get(person.getIdentifier());
				if (entry == null && !person.isLockedDataset()) {
					person.setLockedDataset(true);
					updatedPersons.add(person);

					auditLogger.removedFromDataset(person);
				}
			}
		}

		// Save all the updated people
		if (updatedPersons.size() > 0) {
			personService.saveAll(updatedPersons);
			log.info(updatedPersons.size() + " persons were updated");
		}

		// Save all the created people
		if (createdPersons.size() > 0) {
			createdPersons = personService.saveAll(createdPersons);

			for (Person savedPerson : createdPersons) {
				auditLogger.addedToDataset(savedPerson);
			}

			log.info(createdPersons.size() + " persons were created");
		}

		return ResponseEntity.ok().build();
	}

	private Map<String, CoreDataEntry> coredataToMap(CoreData coreData) {
		Map<String, CoreDataEntry> coreDataMap = new HashMap<>();
		for (CoreDataEntry coreDataEntry : coreData.getEntryList()) {
			String identifier =
					coreData.getDomain() + ":" +
					coreDataEntry.getUuid() + ":" +
					coreDataEntry.getCpr() +
					(!StringUtils.isEmpty(coreDataEntry.getSamAccountName()) ? ":" + coreDataEntry.getSamAccountName() : "");
			coreDataMap.put(identifier, coreDataEntry);
		}
		return coreDataMap;
	}

	private String validateInput(CoreData coreData, boolean fullLoad) {
		List<CoreDataEntry> entries = coreData.getEntryList();

		for (CoreDataEntry entry : entries) {
			entry.setDomain(coreData.getDomain());

			if (entry.getUuid() == null || entry.getUuid().length() != 36) {
				return "Person med ugyldigt UUID: " + entry.getUuid();
			}
			else if (StringUtils.isEmpty(entry.getName())) {
				return "Person uden navn: " + entry.getUuid();
			}
			else if (StringUtils.isEmpty(entry.getCpr())) {
				return "Person uden CPR nummer: " + entry.getUuid();
			}
			else if (entry.getCpr().length() != 10) {
				return "Person med ugyldigt CPR nummer: " + entry.getUuid();
			}

			// SAMAccountName validation
			if (entry.getSamAccountName() != null) {
				// Check if theres duplicate SAMAccountNames in received data (all received data has the same domain)
				long matches = entries
						.stream()
						.filter(otherEntry -> Objects.equals(entry.getSamAccountName(), otherEntry.getSamAccountName()))
						.count();

				if (matches > 1) {
					return "Person med tilknyttet AD konto som anden person i payload også har: " + entry.getUuid();
				}

				// Check database to see if there's an object that matches on SAMAccountName and domain, which is not the same person (Skipped for null values)
				if (!StringUtils.isEmpty(entry.getSamAccountName())) {
					List<Person> dbMatches = personService.getBySamaccountNameAndDomain(entry.getSamAccountName(), coreData.getDomain());
					if (dbMatches != null && !dbMatches.isEmpty()) {
						for (Person match : dbMatches) {
							if (!CoreDataEntry.compare(match, entry)) {
								if (match.isLockedDataset()) {
									personService.delete(match);
								} else {
									return "Person med tilknyttet AD konto som anden person også har: " + entry.getUuid();
								}
							}
						}
					}
				}
			}
		}

		return null;
	}

	private Person createPerson(CoreDataEntry coreDataEntry, String domain) {
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
		person.setNsisLevel(NSISLevel.LOW);
		person.setUuid(coreDataEntry.getUuid());
		person.setAttributes(coreDataEntry.getAttributes());

		return person;
	}

	private boolean updatePerson(CoreDataEntry coreDataEntry, Person person, String domain) {
		boolean modified = false;

		if (!Objects.equals(person.getName(), coreDataEntry.getName())) {
			person.setName(coreDataEntry.getName());
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
			person.setNsisLevel(NSISLevel.LOW);

			auditLogger.addedToDataset(person);
			modified = true;
		}

		if (!Objects.equals(person.getSamaccountName(), coreDataEntry.getSamAccountName())) {
			person.setSamaccountName(StringUtils.isEmpty(coreDataEntry.getSamAccountName()) ? null : coreDataEntry.getSamAccountName());
			modified = true;
		}

		if (!Objects.equals(person.getAttributes(), coreDataEntry.getAttributes())) {
			person.setAttributes(coreDataEntry.getAttributes());
			modified = true;
		}

		return modified;
	}
}
