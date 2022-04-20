package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.api.dto.CoreData;
import dk.digitalidentity.api.dto.CoreDataDelete;
import dk.digitalidentity.api.dto.CoreDataDeleteEntry;
import dk.digitalidentity.api.dto.CoreDataDeltaJfr;
import dk.digitalidentity.api.dto.CoreDataDeltaJfrEntry;
import dk.digitalidentity.api.dto.CoreDataEntry;
import dk.digitalidentity.api.dto.CoreDataFullJfr;
import dk.digitalidentity.api.dto.CoreDataFullJfrEntry;
import dk.digitalidentity.api.dto.CoreDataGroup;
import dk.digitalidentity.api.dto.CoreDataGroupLoad;
import dk.digitalidentity.api.dto.CoreDataNsisAllowed;
import dk.digitalidentity.api.dto.Jfr;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
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
	private GroupService groupService;

	@Autowired
	private AuditLogger auditLogger;

	@Transactional(rollbackFor = Exception.class)
	public void load(CoreData coreData, boolean fullLoad) throws IllegalArgumentException {
		List<Person> updatedPersons = new ArrayList<>();
		List<Person> createdPersons = new ArrayList<>();

		Domain domain = domainService.getByName(coreData.getDomain());
		if (domain == null) {
			throw new IllegalArgumentException("Ukendt domæne: " + coreData.getDomain());
		}

		List<Domain> subDomains = domain.getChildDomains() != null ? domain.getChildDomains() : new ArrayList<>();
		HashMap<String, Domain> subDomainMap = new HashMap<>(subDomains.stream().collect(Collectors.toMap(Domain::getName, Function.identity())));

		// Get people from the database
		List<Person> personList = personService.getByDomain(coreData.getDomain(), true);
		Map<String, Person> personMap = personList.stream().collect(Collectors.toMap(Person::getIdentifier, Function.identity()));

		// Validate input
		String result = validateInput(coreData, personList, subDomains);
		if (result != null) {
			throw new IllegalArgumentException(result);
		}

		ensureUniqueSAMAccountNames(coreData, personList);

		// Go through the received list of entries, check if there's a matching person object, then update/create
		for (CoreDataEntry coreDataEntry : coreData.getEntryList()) {
			Person person = personMap.get(coreDataEntry.getIdentifier());

			if (person != null) {
				boolean modified = updatePerson(coreDataEntry, domain, person, subDomainMap);

				if (modified) {
					updatedPersons.add(person);
				}
			}
			else {
				person = createPerson(coreDataEntry, domain, subDomainMap);
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
					person.getAttributes().clear();
					person.getKombitJfrs().clear();
					person.getGroups().clear();
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

	public CoreData getByDomain(String domainName) throws IllegalArgumentException {
		if (!StringUtils.hasLength(domainName)) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}

		// Get domain and validate it
		Domain domain = domainService.getByName(domainName);
		if (domain == null || domain.getParent() != null) {
			throw new IllegalArgumentException("Domain was either null or not a master domain");
		}

		// Create response object
		CoreData coreData = new CoreData();
		coreData.setDomain(domainName);

		// Add people from domain or any sub-domains
		List<Person> byDomainAndCpr = personService.getByDomain(domainName, true);
		if (byDomainAndCpr.isEmpty()) {
			return coreData;
		}

		// Convert Person objects to CoreData entries
		List<CoreDataEntry> matches = byDomainAndCpr.stream().map(CoreDataEntry::new).collect(Collectors.toList());
		coreData.setEntryList(matches);

		return coreData;
	}

	public CoreData getByDomainAndCpr(Domain domain, String cpr) throws IllegalArgumentException {
		if (domain == null) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}

		if (!StringUtils.hasLength(cpr)) {
			throw new IllegalArgumentException("Cpr cannot be empty");
		}

		// Get domain and validate it
		if (domain.getParent() != null) {
			throw new IllegalArgumentException("Domain was not a master domain");
		}

		// Create response object
		CoreData coreData = new CoreData();
		coreData.setDomain(domain.getName());

		// Add matching people
		List<Person> byDomainAndCpr = personService.getByDomainAndCpr(domain, cpr, true);
		if (byDomainAndCpr == null) {
			return coreData;
		}

		List<CoreDataEntry> matches = byDomainAndCpr.stream().map(CoreDataEntry::new).collect(Collectors.toList());
		coreData.setEntryList(matches);

		return coreData;
	}

	@Transactional(rollbackFor = Exception.class)
	public void lockDataset(CoreDataDelete coreDataDelete) throws IllegalArgumentException {
		if (!StringUtils.hasLength(coreDataDelete.getDomain())) {
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
			List<Person> matches = personService.getByDomainAndCpr(domain, entry.getCpr(), true);

			if (matches != null) {
				for (Person match : matches) {
					if (Objects.equals(match.getSamaccountName(), entry.getSamAccountName()) && Objects.equals(match.getUuid(), entry.getUuid())) {
						match.setLockedDataset(true);
						match.getAttributes().clear();
						match.getKombitJfrs().clear();
						match.getGroups().clear();
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
	
	private void ensureUniqueSAMAccountNames(CoreData coreData, List<Person> databasePeople) {
		List<CoreDataEntry> entries = coreData.getEntryList();
		
		// Map people from database to a list of matching SAMAccountNames, ignore null or empty
		HashMap<String, List<Person>> listOfMatches = new HashMap<>();
		for (Person person : databasePeople) {
			if (StringUtils.hasLength(person.getSamaccountName())) {
				List<Person> people = listOfMatches.get(person.getSamaccountName());

				if (people == null) {
					people = new ArrayList<>();
				}
				people.add(person);

				listOfMatches.put(person.getSamaccountName(), people);
			}
		}

		for (CoreDataEntry entry : entries) {

			// the incoming payload does not have duplicate sAMAccountNames (true for both full and delta payloads), but
			// there might be users in the database that have the existing sAMAccountName. If this happens, we need to
			// null the associated sAMAccountName on the existing database-entry (if it is does not match the identifier
			// for the incoming payload) - worst case, if there is an error in the import logic on the other side, this
			// will result in flip-flopping, but that means they have associcated the same userId with multiple persons
			// on the other end which is unlikely (but could happen)

			if (StringUtils.hasLength(entry.getSamAccountName())) {
				List<Person> dbMatches = listOfMatches.get(entry.getSamAccountName());

				if (dbMatches != null && dbMatches.size() > 0) {
					for (Person match : dbMatches) {
						if (!CoreDataEntry.compare(match, entry)) {
							match.setSamaccountName(null);
							personService.save(match);

							log.warn("Moved sAMAccountName = " + entry.getSamAccountName() + " from " + match.getIdentifier() + " to " + entry.getIdentifier());
						}
					}
				}
			}
		}
	}

	private String validateInput(CoreData coreData, List<Person> databasePeople, List<Domain> subDomains) {
		List<CoreDataEntry> entries = coreData.getEntryList();

		if (entries == null || entries.size() == 0) {
			return "Tom entryList for domæne " + coreData.getDomain();
		}

		Domain parentDomain = domainService.findByName(coreData.getDomain());
		if (parentDomain == null) {
			return "Angivne domæne (" + coreData.getDomain() + ") findes ikke i os2faktor login";
		}

		if (parentDomain.getParent() != null) {
			return "Angivne domæne (" + coreData.getDomain() + ") er et sub-domæne";
		}

		// convert "" to null for more correct handling
		entries.forEach(e -> {
			if (!StringUtils.hasLength(e.getSamAccountName())) {
				e.setSamAccountName(null);
			}
		});

		// Check for duplicate SAMAccountNames and unknown subDomain
		Set<String> existingSAMAccountNames = new HashSet<>();
		for (CoreDataEntry entry : entries) {
			if (StringUtils.hasLength(entry.getSubDomain()) && !subDomains.stream().anyMatch(d -> Objects.equals(d.getName(), entry.getSubDomain()))) {
				return "Ukendt underdomæne: " + entry.getSubDomain();
			}

			if (StringUtils.hasLength(entry.getSamAccountName())) {
				if (!existingSAMAccountNames.contains(entry.getSamAccountName().toLowerCase())) {
					existingSAMAccountNames.add(entry.getSamAccountName().toLowerCase());
				}
				else {
					return "Flere personer med tilknyttet AD konto (" + entry.getSamAccountName() + ") for domæne " + coreData.getDomain();
				}
			}
		}

		for (CoreDataEntry entry : entries) {
			// copy domain to each entry - we need it for the entity identifier and for comparison
			entry.setDomain(coreData.getDomain());

			if (entry.getUuid() == null || entry.getUuid().length() != 36) {
				return "Person med ugyldigt UUID: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
			else if (!StringUtils.hasLength(entry.getName())) {
				return "Person uden navn: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
			else if (!StringUtils.hasLength(entry.getCpr())) {
				return "Person uden CPR nummer: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
			else if (entry.getCpr().length() != 10) {
				return "Person med ugyldigt CPR nummer: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
		}

		return null;
	}

	private Person createPerson(CoreDataEntry coreDataEntry, Domain domain, HashMap<String, Domain> subDomainMap) {
		Person person = new Person();
		person.setSamaccountName(coreDataEntry.getSamAccountName());
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

		// set subDomain if specified, otherwise use normal domain
		if (StringUtils.hasLength(coreDataEntry.getSubDomain())) {
			Domain coreDataEntryDomain = subDomainMap.get(coreDataEntry.getSubDomain());
			if (coreDataEntryDomain == null) {
				// will not actually happen, as we validate for this before we reach this point... this would be a coding error if this happens
				throw new RuntimeException("unknown domain: " + coreDataEntry.getSubDomain());
			}

			person.setDomain(coreDataEntryDomain);
		}
		else {
			person.setDomain(domain);
		}

		return person;
	}

	private boolean updatePerson(CoreDataEntry coreDataEntry, Domain domain, Person person, HashMap<String, Domain> subDomainMap) {
		boolean modified = false;

		// find the associated domain for this user (might be a subdomain)
		Domain coreDataEntryDomain = domain;
		if (StringUtils.hasLength(coreDataEntry.getSubDomain())) {
			coreDataEntryDomain = subDomainMap.get(coreDataEntry.getSubDomain());
		}

		// update if needed
		if (!Objects.equals(coreDataEntryDomain, person.getDomain())) {
			person.setDomain(coreDataEntryDomain);
			modified = true;
		}

		if (!Objects.equals(person.getNameAlias(), coreDataEntry.getName())) {
			person.setNameAlias(coreDataEntry.getName());
			modified = true;
		}

		if (!Objects.equals(person.getEmail(), coreDataEntry.getEmail())) {
			person.setEmail(!StringUtils.hasLength(coreDataEntry.getEmail()) ? null : coreDataEntry.getEmail());
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
				auditLogger.nsisAllowedChanged(person, true);
			}
			else {
				person.setNsisAllowed(false);
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisPassword(null);
				auditLogger.nsisAllowedChanged(person, false);
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

	@Transactional
	public void loadFullKombitJfr(CoreDataFullJfr coreData) {
		List<Person> persons = personService.getByDomain(coreData.getDomain(), true);

		Map<String, Person> personMapSAMAccountName = persons.stream().filter(p -> StringUtils.hasLength(p.getSamaccountName())).collect(Collectors.toMap(Person::getLowerSamAccountName, Function.identity()));
		Map<String, Person> personMapAzureId = persons.stream().filter(p -> p.getAzureId() != null).collect(Collectors.toMap(Person::getAzureId, Function.identity()));
		Map<String, Person> personMap = persons.stream().filter(p -> p.getUuid() != null).collect(Collectors.toMap(Person::getUuid, Function.identity()));

		// add/update case
		for (CoreDataFullJfrEntry entry : coreData.getEntryList()) {
			Person person = null;
			if (StringUtils.hasLength(entry.getUuid())) {
				// try against Azure in case Azure users are loaded into this domain
				if (personMapAzureId.size() > 0) {
					person = personMapAzureId.get(entry.getUuid());
				}
				else {
					// otherwise use the ordinary UUID (for most other muni's
					person = personMap.get(entry.getUuid());
				}
			}
			else {
				person = personMapSAMAccountName.get(entry.getSamAccountName().toLowerCase());
			}

			if (person == null) {
				log.warn("Got person that does not exist in OS2faktor yet: " + (entry.getSamAccountName() != null ? entry.getSamAccountName() : "<null>") + " (" + (entry.getUuid() != null ? entry.getUuid() : "<null>") + ")");
				continue;
			}

			boolean changes = false;

			// add case
			if (entry.getJfrs() != null) {
				for (Jfr entryJfr : entry.getJfrs()) {
					boolean found = false;

					for (KombitJfr personJfr : person.getKombitJfrs()) {
						if (Objects.equals(personJfr.getCvr(), entryJfr.getCvr()) && Objects.equals(personJfr.getIdentifier(), entryJfr.getIdentifier())) {
							found = true;
							break;
						}
					}

					if (!found) {
						KombitJfr newJfr = new KombitJfr();
						newJfr.setCvr(entryJfr.getCvr());
						newJfr.setIdentifier(entryJfr.getIdentifier());
						newJfr.setPerson(person);
						person.getKombitJfrs().add(newJfr);

						changes = true;
					}
				}
			}

			// update case (well, remove to be honest)
			for (Iterator<KombitJfr> iterator = person.getKombitJfrs().iterator(); iterator.hasNext(); ) {
				KombitJfr personJfr = iterator.next();
				boolean found = false;

				if (entry.getJfrs() != null) {
					for (Jfr entryJfr : entry.getJfrs()) {
						if (Objects.equals(personJfr.getCvr(), entryJfr.getCvr()) && Objects.equals(personJfr.getIdentifier(), entryJfr.getIdentifier())) {
							found = true;
							break;
						}
					}
				}

				if (!found) {
					iterator.remove();
					changes = true;
				}
			}

			if (changes) {
				personService.save(person);
			}
		}

		Map<String, CoreDataFullJfrEntry> coreDataMapSAMAccountName = coreData.getEntryList().stream().filter(entry -> StringUtils.hasLength(entry.getSamAccountName())).collect(Collectors.toMap(CoreDataFullJfrEntry::getLowerSamAccountName, Function.identity()));
		Map<String, CoreDataFullJfrEntry> coreDataMapUuid = coreData.getEntryList().stream().filter(entry -> StringUtils.hasLength(entry.getUuid())).collect(Collectors.toMap(CoreDataFullJfrEntry::getUuid, Function.identity()));

		// remove those not in map
		for (Person person : persons) {
			// ignore those without kombit roles
			if (person.getKombitJfrs() == null || person.getKombitJfrs().size() == 0) {
				continue;
			}

			boolean remove = false;
			if (coreDataMapSAMAccountName.get(person.getLowerSamAccountName()) == null && coreDataMapUuid.get(person.getAzureId()) == null) {
				remove = true;
			}

			if (remove) {
				person.getKombitJfrs().removeIf(p -> p != null);
				personService.save(person);
			}
		}
	}

	@Transactional
	public void loadDeltaKombitJfr(CoreDataDeltaJfr coreData) {
		List<Person> persons = personService.getByDomain(coreData.getDomain(), true);

		Map<String, Person> personMapSAMAccountName = persons.stream().filter(p -> StringUtils.hasLength(p.getSamaccountName())).collect(Collectors.toMap(Person::getLowerSamAccountName, Function.identity()));
		Map<String, Person> personMapAzureId = persons.stream().filter(p -> p.getAzureId() != null).collect(Collectors.toMap(Person::getAzureId, Function.identity()));

		// add/update case
		for (CoreDataDeltaJfrEntry entry : coreData.getEntryList()) {
			Person person = null;
			if (StringUtils.hasLength(entry.getUuid())) {
				person = personMapAzureId.get(entry.getUuid());
			}
			else if (StringUtils.hasLength(entry.getSamAccountName())) {
				person = personMapSAMAccountName.get(entry.getLowerSamAccountName());
			}

			if (person == null) {
				log.warn("Got person that does not exist in OS2faktor yet: " + (entry.getSamAccountName() != null ? entry.getSamAccountName() : "<null>") + " (" + (entry.getUuid() != null ? entry.getUuid() : "<null>") + ")");
				continue;
			}

			boolean changes = false;

			// adds
			if (entry.getAddJfrs() != null) {
				for (Jfr entryJfr : entry.getAddJfrs()) {
					boolean found = false;

					for (KombitJfr personJfr : person.getKombitJfrs()) {
						if (Objects.equals(personJfr.getCvr(), entryJfr.getCvr()) && Objects.equals(personJfr.getIdentifier(), entryJfr.getIdentifier())) {
							found = true;
							break;
						}
					}

					if (!found) {
						KombitJfr newJfr = new KombitJfr();
						newJfr.setCvr(entryJfr.getCvr());
						newJfr.setIdentifier(entryJfr.getIdentifier());
						newJfr.setPerson(person);
						person.getKombitJfrs().add(newJfr);

						changes = true;
					}
				}
			}

			// removes
			if (entry.getRemoveJfrs() != null) {
				for (Jfr entryJfr : entry.getRemoveJfrs()) {
					for (Iterator<KombitJfr> iterator = person.getKombitJfrs().iterator(); iterator.hasNext(); ) {
						KombitJfr personJfr = iterator.next();

						if (Objects.equals(personJfr.getCvr(), entryJfr.getCvr()) && Objects.equals(personJfr.getIdentifier(), entryJfr.getIdentifier())) {
							iterator.remove();
							changes = true;
							break;
						}
					}
				}
			}

			if (changes) {
				personService.save(person);
			}
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void loadNsisUsers(CoreDataNsisAllowed nsisAllowed) {
		if (nsisAllowed.getNsisUserUuids() != null) {
			Domain domain = domainService.getByName(nsisAllowed.getDomain());
			if (domain == null) {
				throw new IllegalAccessError("Unknown domain: " + nsisAllowed.getDomain());
			}

			List<Person> domainPeople = personService.getByDomain(domain, true);

			List<Person> toBeSaved = new ArrayList<>();
			for (Person domainPerson : domainPeople) {
				boolean shouldNsisBeAllowed = nsisAllowed.getNsisUserUuids().contains(domainPerson.getUuid());
				boolean currentNsisAllowed = domainPerson.isNsisAllowed();

				if (shouldNsisBeAllowed != currentNsisAllowed) {
					domainPerson.setNsisAllowed(shouldNsisBeAllowed);
					toBeSaved.add(domainPerson);
					auditLogger.nsisAllowedChanged(domainPerson, shouldNsisBeAllowed);
				}
			}

			personService.saveAll(toBeSaved);
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void loadGroupsDelta(CoreDataGroupLoad coreDataGroupLoad) {
		loadGroups(coreDataGroupLoad, false);
	}

	@Transactional(rollbackFor = Exception.class)
	public void loadGroupsFull(CoreDataGroupLoad coreDataGroupLoad) {
		loadGroups(coreDataGroupLoad, true);
	}
	
	private void loadGroups(CoreDataGroupLoad coreDataGroupLoad, boolean full) {
		Domain domain = domainService.getByName(coreDataGroupLoad.getDomain());
		if (domain == null) {
			throw new IllegalAccessError("Ukendt domæne: " + coreDataGroupLoad.getDomain());
		}

		Map<String, CoreDataGroup> coreDataGroupMap = coreDataGroupLoad.getGroups().stream().collect(Collectors.toMap(CoreDataGroup::getUuid, Function.identity()));

		List<Group> groups = groupService.getAll();
		Map<String, Group> groupMap = groups.stream().collect(Collectors.toMap(Group::getUuid, Function.identity()));

		List<Person> allPersons = personService.getByDomain(domain, true);
		Map<String, Person> personMap = allPersons.stream()
				.filter(p -> p.getSamaccountName() != null)
				.collect(Collectors.toMap(Person::getLowerSamAccountName, Function.identity()));

		// create or update groups
		for (Map.Entry<String, CoreDataGroup> entry : coreDataGroupMap.entrySet()) {
			CoreDataGroup coreDataGroup = entry.getValue();
			boolean changes = false;
			Group group = null;

			if (groupMap.containsKey(entry.getKey())) {
				group = groupMap.get(entry.getKey());

				// update name/description if needed
				if (!Objects.equals(group.getName(), coreDataGroup.getName())) {
					group.setName(coreDataGroup.getName());
					changes = true;
				}
				
				if (!Objects.equals(group.getDescription(), coreDataGroup.getDescription())) {
					group.setDescription(coreDataGroup.getDescription());
					changes = true;
				}
			}
			else {
				group = new Group();
				group.setUuid(coreDataGroup.getUuid());
				group.setName(coreDataGroup.getName());
				group.setDescription(coreDataGroup.getDescription());
				group.setMembers(new ArrayList<PersonGroupMapping>());
				group.setDomain(domain);

				log.info("Adding new group: " + group.getName() + " (" + group.getUuid() + ")");

				changes = true;
			}

			// add members
			for (String member : coreDataGroup.getMembers()) {
				boolean found = false;

				Person personToAdd = personMap.get(member.toLowerCase());
				if (personToAdd == null) {
					// TODO: we can remove this code sometime in the future, when we know that everyone has upgraded to using sAMAccountName in CoreData
					for (Person person : allPersons) {
						if (Objects.equals(person.getUuid(), member)) {
							personToAdd = person;
							break;
						}
					}
					
					if (personToAdd == null) {
						log.warn("Group payload contains sAMAccountName of person that does not exist:" + member);
						continue;
					}
				}

				for (Person person : group.getMembers()) {
					if (Objects.equals(person.getUuid(), member)) {
						found = true;
						break;
					}
				}

				if (!found) {
					log.info("Adding user to group (" + group.getName() + ") : " + PersonService.getUsername(personToAdd));
					PersonGroupMapping pgm = new PersonGroupMapping(personToAdd, group);
					group.getMemberMapping().add(pgm);
					
					changes = true;
				}
			}
			
			// remove members
			for (Iterator<PersonGroupMapping> iterator = group.getMemberMapping().iterator(); iterator.hasNext();) {
				PersonGroupMapping pgm = iterator.next();
				boolean found = false;
				
				for (String member : coreDataGroup.getMembers()) {
					// TODO: can remove the UUID check in the future (once everyone is running latest CoreData
					if (Objects.equals(pgm.getPerson().getLowerSamAccountName(), member.toLowerCase()) || Objects.equals(pgm.getPerson().getUuid(), member)) {
						found = true;
						break;
					}
				}

				if (!found) {
					log.info("Removing user from group (" + group.getName() + ") : " + PersonService.getUsername(pgm.getPerson()));
					iterator.remove();
					changes = true;
				}
			}
			
			if (changes) {
				log.info("Persisting changes to group: " + group.getName() + " (" + group.getUuid() + ")");
				groupService.save(group);
			}
		}

		// in "full-mode" perform deletes on any group not included in payload
		if (full) {
			for (Group group : groups) {
				if (!Objects.equals(group.getDomain().getName(), coreDataGroupLoad.getDomain())) {
					continue;
				}
				
				boolean found = false;
				
				for (String groupUuid : coreDataGroupMap.keySet()) {
					if (Objects.equals(group.getUuid(), groupUuid)) {
						found = true;
						break;
					}
				}
				
				if (!found) {
					log.info("Deleting group: " + group.getName() + " (" + group.getUuid() + ")");
					groupService.delete(group);
				}
			}
		}
	}
}
