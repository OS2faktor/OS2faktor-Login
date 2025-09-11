package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dk.digitalidentity.api.dto.CoreDataForceChangePassword;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.service.EmailTemplateService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.api.CoreDataApi;
import dk.digitalidentity.api.dto.CoreData;
import dk.digitalidentity.api.dto.CoreDataDelete;
import dk.digitalidentity.api.dto.CoreDataDeleteEntry;
import dk.digitalidentity.api.dto.CoreDataDeltaJfr;
import dk.digitalidentity.api.dto.CoreDataDeltaJfrEntry;
import dk.digitalidentity.api.dto.CoreDataEntry;
import dk.digitalidentity.api.dto.CoreDataEntryLight;
import dk.digitalidentity.api.dto.CoreDataFullJfr;
import dk.digitalidentity.api.dto.CoreDataFullJfrEntry;
import dk.digitalidentity.api.dto.CoreDataGroup;
import dk.digitalidentity.api.dto.CoreDataGroupLoad;
import dk.digitalidentity.api.dto.CoreDataKombitAttributeEntry;
import dk.digitalidentity.api.dto.CoreDataKombitAttributesLoad;
import dk.digitalidentity.api.dto.CoreDataNemLoginAllowed;
import dk.digitalidentity.api.dto.CoreDataNemLoginEntry;
import dk.digitalidentity.api.dto.CoreDataExtendedLookup;
import dk.digitalidentity.api.dto.CoreDataNemLoginStatus;
import dk.digitalidentity.api.dto.CoreDataNsisAllowed;
import dk.digitalidentity.api.dto.CoreDataStatus;
import dk.digitalidentity.api.dto.CoreDataStatusEntry;
import dk.digitalidentity.api.dto.Jfr;
import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.MitidErhvervCache;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PersonStatistics;
import dk.digitalidentity.common.dao.model.Supporter;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.MessageQueueService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.PersonStatisticsService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.nemlogin.service.NemLoginService;
import dk.digitalidentity.nemlogin.service.model.FullEmployee;
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
	
	@Autowired
	private EmailTemplateSenderService emailTemplateSenderService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private MessageQueueService messageQueueService;
	
	@Autowired
	private PersonStatisticsService personStatisticsService;
	
	@Autowired
	private NemLoginService nemLoginService;
	
	@Autowired
	private MitidErhvervCacheService mitIdErhvervCacheService;
		
	// thread-safe formatter
	private DateTimeFormatter formatter = new DateTimeFormatterBuilder()
	        .appendPattern("yyyy-MM-dd[ HH:mm:ss]")
	        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
	        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
	        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
	        .toFormatter();

	public void load(CoreData coreData, boolean fullLoad) throws IllegalArgumentException {
		Domain domain = domainService.getByName(coreData.getDomain(), d -> {
			d.getChildDomains().size();
			if (d.getParent() != null) {
				d.getParent().getName();
			}
		});

		if (domain == null) {
			throw new IllegalArgumentException("Ukendt domæne: " + coreData.getDomain());
		}

		// this will ensure "cleanup" is only done within this subdomain - note that if someone calls the api with a globalSubDomain
		// and any of the sub-entries has a different subDomain, this consists of an error and the payload will be rejected
		Domain globalSubDomain = null;
		if (StringUtils.hasLength(coreData.getGlobalSubDomain())) {
			Domain gsd = domainService.getByName(coreData.getGlobalSubDomain(), d -> {
				d.getChildDomains().size();
				if (d.getParent() != null) {
					d.getParent().getName();
				}				
			});

			if (gsd != null && gsd.getParent() != null) {
				globalSubDomain = gsd;				
			}
			else {
				throw new IllegalArgumentException("Invalid globalSubDomain: " + coreData.getGlobalSubDomain());
			}
		}

		List<Domain> subDomains = domain.getChildDomains() != null ? domain.getChildDomains() : new ArrayList<>();
		if (globalSubDomain != null) {
			// retrict to globalSubdomain
			subDomains = List.of(globalSubDomain);
		}

		// Validate input
		String result = validateInput(coreData, subDomains, globalSubDomain);
		if (result != null) {
			throw new IllegalArgumentException(result);
		}

		List<Person> updatedPersons = new ArrayList<>();
		List<Person> createdPersons = new ArrayList<>();

		HashMap<String, Domain> subDomainMap = new HashMap<>(subDomains.stream().collect(Collectors.toMap(Domain::getName, Function.identity())));

		// Get a list of all account of the domain, even if Global subdomain is set,
		// this is used to move accounts between subdomains without locking and creating a new one
		List<Person> personsByDomain = personService.getByDomain(domain, true, p -> {
			p.getDomain().getName();
			p.getAttributes().size();
			p.getKombitJfrs().size();

			p.getGroups().forEach(gm -> {
				gm.getGroup().getName();
			});			
			
			if (p.getSupporter() != null) {
				p.getSupporter().getDomain().getName();
			}
		});

		Map<String, Person> personMap = personsByDomain
				.stream()
				.collect(Collectors.toMap(Person::getLowerSamAccountName, Function.identity()));

		// Get people from the database (restrict to globalSubDomain if supplied, to ensure cleanup (delete only happens on these users)
		long globalSubDomainId = globalSubDomain != null ? globalSubDomain.getId() : 0;
		List<Person> filteredPersonList = (globalSubDomain == null)
				? personsByDomain
				: personsByDomain.stream().filter(p -> p.getDomain().getId() == globalSubDomainId).collect(Collectors.toList());

		// Go through the received list of entries, check if there's a matching person object, then update/create
		for (CoreDataEntry coreDataEntry : coreData.getEntryList()) {
			Person person = personMap.get(coreDataEntry.getLowerSamAccountName());

			// in the special case that we have an existing user in the DB with the same sAMAccountName, but that account
			// is locked (dataset), then we will only allow reactivation if the cpr+uuid matches, otherwise it is a fresh person
			if (person != null && person.isLockedDataset()) {
				if (!compareCpr(person, coreDataEntry) || !Objects.equals(person.getUuid(), coreDataEntry.getUuid())) {
					// delete (side-effect it gets auditlogged)
					personService.delete(person, null);

					// force creation of a new person
					person = null;
				}
			}

			// in the special case that the CPR changes on an existing person, it should be treated as a delete followed by a create
			if (person != null && !compareCpr(person, coreDataEntry)) {
				// delete (side-effect it gets auditlogged)
				personService.delete(person, null);

				// force creation of a new person
				person = null;				
			}
			
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

			for (Person person : filteredPersonList) {
				CoreDataEntry entry = entryMap.get(person.getLowerSamAccountName());

				if (entry == null && !person.isLockedDataset()) {
					person.setLockedDataset(true);
					person.setLockedDatasetTts(LocalDateTime.now());
					person.getAttributes().clear();
					person.getKombitJfrs().clear();
					person.getGroups().clear();
					removedFromDataset.add(person);
					
					if (person.isNsisAllowed()) {
						sendDeactivateEmail(person);
					}
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

			EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.NEW_USER);
			if (emailTemplate.getChildren().stream().anyMatch(c -> c.isEnabled())) {
				createdPersons.forEach( p -> {
					for (EmailTemplateChild emailTemplateChild : emailTemplate.getChildren()) {
						if (emailTemplateChild.isEnabled() && emailTemplateChild.getDomain().getId() == p.getDomain().getId()) {
							String msg = EmailTemplateService.safeReplacePlaceholder(emailTemplateChild.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, p.getName());
							msg = EmailTemplateService.safeReplacePlaceholder(msg, EmailTemplateService.USERID_PLACEHOLDER, p.getSamaccountName());

							// queue message
							emailTemplateSenderService.send(p.getEmail(), p.getCpr(), p, emailTemplateChild.getTitle(), msg, emailTemplateChild, false);
						}
					}
				});
			}
		}
	}

	private boolean compareCpr(Person person, CoreDataEntry entry) {
		
		// special case for MitID Erhverv users - for these the CPR number is updated
		// during activation, so CoreData might still get the original 0000000000 value
		// for the CPR, but the registered CPR on the person has been updated - here a match
		// on the NemLog-in UUID is enough to constistute a match for updating purposes
		if (commonConfiguration.getMitIdErhverv().isEnabled() &&
			Objects.equals(entry.getCpr(), Constants.NO_CPR_VALUE) &&
			StringUtils.hasText(entry.getExternalNemloginUserUuid())) {

			return (Objects.equals(person.getCpr(), entry.getCpr()) || Objects.equals(person.getExternalNemloginUserUuid(), entry.getExternalNemloginUserUuid()));
		}
		
		return (Objects.equals(person.getCpr(), entry.getCpr()));
	}

	private void sendDeactivateEmail(Person person) {
		EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.PERSON_DEACTIVATED_CORE_DATA);

		for (EmailTemplateChild child : emailTemplate.getChildren()) {
			if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
				String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
				message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
				
				// delay sending for 3 hours, in case this was a mistake (it will get dequeued upon reactivation)
				emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, 180);
			}
		}
		
		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.FULL_SERVICE_IDP_REMOVED);

			for (EmailTemplateChild child : emailTemplate.getChildren()) {
				if (child.getDomain().getId() == person.getDomain().getId()) {
					String message = emailTemplateService.safeReplaceEverything(child.getMessage(), person);

					// delay sending for 3 hours, in case this was a mistake (it will get dequeued upon reactivation)
					emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, 180);
				}
			}			
		}
	}

	public CoreDataStatus getStatusByDomain(String domainName, boolean onlyNsisAllowed) {
		if (!StringUtils.hasLength(domainName)) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}

		// Get domain and validate it
		Domain domain = domainService.getByName(domainName);
		if (domain == null || domain.getParent() != null) {
			throw new IllegalArgumentException("Domain was either null or not a master domain");
		}

		// Create response object
		CoreDataStatus coreData = new CoreDataStatus();
		coreData.setDomain(domainName);

		// Add people from domain or any sub-domains
		List<Person> persons = personService.getByDomain(domainName, true, onlyNsisAllowed);
		if (persons.isEmpty()) {
			return coreData;
		}

		List<PersonStatistics> personStatistics = personStatisticsService.getAll();
		Map<Long, PersonStatistics> personStatisticsMap = personStatistics.stream().collect(Collectors.toMap(PersonStatistics::getPersonId, Function.identity()));
		
		// Convert Person objects to CoreData entries
		List<CoreDataStatusEntry> matches = persons.stream().map(p -> new CoreDataStatusEntry(p, personStatisticsMap.get(p.getId()))).collect(Collectors.toList());
		coreData.setEntryList(matches);

		return coreData;
	}
	
	public CoreDataNemLoginStatus getNemloginStatus(String domainName) {
		if (!StringUtils.hasLength(domainName)) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}

		// Get domain and validate it
		Domain domain = domainService.getByName(domainName);
		if (domain == null || domain.getParent() != null) {
			throw new IllegalArgumentException("Domain was either null or not a master domain");
		}

		CoreDataNemLoginStatus coreData = new CoreDataNemLoginStatus();
		coreData.setDomain(domainName);
		coreData.setEntries(new HashSet<>());
		
		List<Person> persons = personService.getByNemloginUserUuidNotNull();
		for (Person person : persons) {
			// we keep the NemLoginUserUuid for reactivation purposes, but these are actually Suspended inside MitID Erhverv
			if (!person.isTransferToNemlogin()) {
				continue;
			}
			
			CoreDataNemLoginEntry entry = new CoreDataNemLoginEntry();
			entry.setActive(!person.isLocked());
			entry.setCpr(person.getCpr());
			entry.setNemloginUserUuid(person.getNemloginUserUuid());
			entry.setSamAccountName(person.getSamaccountName());
			
			coreData.getEntries().add(entry);
		}
		
		return coreData;
	}
	
	public CoreData getByDomain(String domainName, boolean onlyNsisAllowed) throws IllegalArgumentException {
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
		List<Person> byDomainAndCpr = personService.getByDomain(domainName, true, onlyNsisAllowed);
		if (byDomainAndCpr.isEmpty()) {
			return coreData;
		}

		// Convert Person objects to CoreData entries
		List<CoreDataEntry> matches = byDomainAndCpr.stream().map(CoreDataEntry::new).collect(Collectors.toList());
		coreData.setEntryList(matches);

		return coreData;
	}

	public CoreData getByDomainAndCpr(Domain domain, String cpr) throws IllegalArgumentException {
		domainValidation(domain);

		if (!StringUtils.hasLength(cpr)) {
			throw new IllegalArgumentException("Cpr cannot be empty");
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

	public CoreDataFullJfr getJFRByDomain(Domain domain) throws IllegalArgumentException {
		domainValidation(domain);

		// Create response object
		CoreDataFullJfr coreDataFullJfr = new CoreDataFullJfr();
		coreDataFullJfr.setDomain(domain.getName());

		// Add matching people
		List<Person> byDomainAndCpr = personService.getByDomain(domain, true);
		if (byDomainAndCpr == null) {
			return coreDataFullJfr;
		}

		List<CoreDataFullJfrEntry> matches = byDomainAndCpr.stream().filter(person -> CollectionUtils.isNotEmpty(person.getKombitJfrs())).map(CoreDataFullJfrEntry::new).collect(Collectors.toList());
		coreDataFullJfr.setEntryList(matches);

		return coreDataFullJfr;
	}

	public CoreDataFullJfr getJFRByDomainAndCpr(Domain domain, String cpr) throws IllegalArgumentException {
		domainValidation(domain);

		if (!StringUtils.hasLength(cpr)) {
			throw new IllegalArgumentException("Cpr cannot be empty");
		}

		// Create response object
		CoreDataFullJfr coreDataFullJfr = new CoreDataFullJfr();
		coreDataFullJfr.setDomain(domain.getName());

		// Add matching people
		List<Person> byDomainAndCpr = personService.getByDomainAndCpr(domain, cpr, true);
		if (byDomainAndCpr == null) {
			return coreDataFullJfr;
		}

		List<CoreDataFullJfrEntry> matches = byDomainAndCpr.stream().filter(person -> CollectionUtils.isNotEmpty(person.getKombitJfrs())).map(CoreDataFullJfrEntry::new).collect(Collectors.toList());
		coreDataFullJfr.setEntryList(matches);

		return coreDataFullJfr;
	}

	public CoreDataGroupLoad getGroupsByDomain(Domain domain) throws IllegalArgumentException {
		domainValidation(domain);

		// Create response object
		CoreDataGroupLoad coreDataGroupLoad = new CoreDataGroupLoad();
		coreDataGroupLoad.setDomain(domain.getName());

		List<Group> groups = groupService.getByDomain(domain);
		if (groups == null) {
			return coreDataGroupLoad;
		}

		List<CoreDataGroup> groupEntries = groups.stream().map(CoreDataGroup::new).collect(Collectors.toList());
		coreDataGroupLoad.setGroups(groupEntries);

		return coreDataGroupLoad;
	}

	public CoreDataGroupLoad getGroupsByDomainAndCpr(Domain domain, String cpr) throws IllegalArgumentException {
		domainValidation(domain);

		if (!StringUtils.hasLength(cpr)) {
			throw new IllegalArgumentException("Cpr cannot be empty");
		}

		// Create response object
		CoreDataGroupLoad coreDataGroupLoad = new CoreDataGroupLoad();
		coreDataGroupLoad.setDomain(domain.getName());

		List<Person> byDomainAndCpr = personService.getByDomainAndCpr(domain, cpr, true);

		HashSet<Group> distinctGroups = new HashSet<>();
		CollectionUtils.emptyIfNull(byDomainAndCpr)
				.stream()
				.map(person ->
					 CollectionUtils.emptyIfNull(person.getGroups())
							.stream()
							.map(PersonGroupMapping::getGroup)
							.collect(Collectors.toSet())
				)
				.forEach(distinctGroups::addAll);

		List<CoreDataGroup> groupEntries = distinctGroups.stream().map(CoreDataGroup::new).collect(Collectors.toList());
		coreDataGroupLoad.setGroups(groupEntries);

		return coreDataGroupLoad;
	}

	private static void domainValidation(Domain domain) {
		if (domain == null) {
			throw new IllegalArgumentException("Domain cannot be empty");
		}

		// Get domain and validate it
		if (domain.getParent() != null) {
			throw new IllegalArgumentException("Domain was not a master domain");
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void deleteDataset(CoreDataDelete coreDataDelete) throws IllegalArgumentException {
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

		List<Person> toDelete = new ArrayList<Person>();
		for (CoreDataDeleteEntry entry : coreDataDelete.getEntryList()) {
			List<Person> matches = personService.getByDomainAndCpr(domain, entry.getCpr(), true);

			if (matches != null) {
				for (Person match : matches) {
					if (match.getSamaccountName().equalsIgnoreCase(entry.getSamAccountName())) {
						toDelete.add(match);
					}
				}
			}
		}

		if (toDelete.size() > 0) {
			personService.deleteAll(toDelete);
			auditLogger.deleteFromDataset(toDelete);
		}
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
					
					if (match.getSamaccountName().equalsIgnoreCase(entry.getSamAccountName())) {
						if (!match.isLockedDataset()) {
							match.setLockedDataset(true);
							match.setLockedDatasetTts(LocalDateTime.now());
							match.getAttributes().clear();
							match.getKombitJfrs().clear();
							match.getGroups().clear();
							toSave.add(match);

							if (match.isNsisAllowed()) {
								sendDeactivateEmail(match);
							}
						}
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
			coreDataMap.put(coreDataEntry.getLowerSamAccountName(), coreDataEntry);
		}

		return coreDataMap;
	}
	
	private String validateInput(CoreDataKombitAttributesLoad kombitAttributes, List<Person> databasePeople) {
		List<CoreDataKombitAttributeEntry> entries = kombitAttributes.getEntryList();

		if (entries == null || entries.size() == 0) {
			return "Tom entryList for domæne " + kombitAttributes.getDomain();
		}

		Domain parentDomain = domainService.findByName(kombitAttributes.getDomain());
		if (parentDomain == null) {
			return "Angivne domæne (" + kombitAttributes.getDomain() + ") findes ikke i OS2faktor login";
		}

		if (parentDomain.getParent() != null) {
			return "Angivne domæne (" + kombitAttributes.getDomain() + ") er et sub-domæne";
		}

		// convert "" to null for more correct handling
		entries.forEach(e -> {
			if (!StringUtils.hasLength(e.getSamAccountName())) {
				e.setSamAccountName(null);
			}
		});

		// check for missing sAMAccountNames
		for (CoreDataKombitAttributeEntry entry : entries) {
			if (!StringUtils.hasLength(entry.getSamAccountName())) {
				return "Person mangler sAMAccountName for domæne " + kombitAttributes.getDomain();
			}
		}
		
		// Check for duplicate SAMAccountNames
		Set<String> existingSAMAccountNames = new HashSet<>();
		for (CoreDataKombitAttributeEntry entry : entries) {
			if (StringUtils.hasLength(entry.getSamAccountName())) {
				if (!existingSAMAccountNames.contains(entry.getSamAccountName().toLowerCase())) {
					existingSAMAccountNames.add(entry.getSamAccountName().toLowerCase());
				}
				else {
					return "Flere personer med tilknyttet AD konto (" + entry.getSamAccountName() + ") for domæne " + kombitAttributes.getDomain();
				}
			}
		}

		return null;
	}

	private String validateInput(CoreData coreData, List<Domain> subDomains, Domain globalSubDomain) {
		List<CoreDataEntry> entries = coreData.getEntryList();

		if (entries == null || entries.size() == 0) {
			return "Tom entryList for domæne " + coreData.getDomain();
		}

		Domain parentDomain = domainService.findByName(coreData.getDomain());
		if (parentDomain == null) {
			return "Angivne domæne (" + coreData.getDomain() + ") findes ikke i os2faktor login";
		}

		if (globalSubDomain == null && parentDomain.getParent() != null) {
			return "Angivne domæne (" + coreData.getDomain() + ") er et sub-domæne";
		}

		// remove null entries :)
		entries.removeIf(e -> e == null);
		
		// remove null/empty entries from attributes
		for (CoreDataEntry entry : entries) {
			for (Iterator<Entry<String, String>> iterator = entry.getAttributes().entrySet().iterator(); iterator.hasNext();) {
				Entry<String, String> mapEntry = iterator.next();
				String value = mapEntry.getValue();
				
				if (!StringUtils.hasLength(value)) {
					iterator.remove();
				}
			}
		}
		
		// make sure all entries have a sAMAccountName
		CoreDataEntry nullEntry = entries.stream().filter(e -> !StringUtils.hasLength(e.getSamAccountName())).findAny().orElse(null);
		if (nullEntry != null) {
			return "Bruger med uuid '" + nullEntry.getUuid() + "' mangler et brugernavn";
		}

		// Check for duplicate SAMAccountNames and unknown subDomain
		Set<String> existingSAMAccountNames = new HashSet<>();
		for (CoreDataEntry entry : entries) {
			if (globalSubDomain != null) {
				// no subdomain supplied on entry, but a global subdomain has been supplied, so set that on the user
				if (!StringUtils.hasLength(entry.getSubDomain())) {
					entry.setSubDomain(globalSubDomain.getName());
				}

				if (!Objects.equals(globalSubDomain.getName(), entry.getSubDomain())) {
					return "Underdomæne mismatch: " + globalSubDomain.getName() + " != " + entry.getSubDomain();
				}
			}
			else if (StringUtils.hasLength(entry.getSubDomain()) && !subDomains.stream().anyMatch(d -> Objects.equals(d.getName(), entry.getSubDomain()))) {
				return "Ukendt underdomæne: " + entry.getSubDomain();
			}

			// fix empty string email
			if (entry.getEmail() != null && !StringUtils.hasLength(entry.getEmail())) {
				entry.setEmail(null);
			}

			if (!existingSAMAccountNames.contains(entry.getSamAccountName().toLowerCase())) {
				existingSAMAccountNames.add(entry.getSamAccountName().toLowerCase());
			}
			else {
				return "Flere personer med tilknyttet AD konto (" + entry.getSamAccountName() + ") for domæne " + coreData.getDomain();
			}
		}
		
		// remove entries with invalid cpr's
		entries.removeIf(e -> {
			if (!StringUtils.hasLength(e.getCpr())) {
				log.warn("Fjernet entry for Person uden cpr: " + e.getUuid() + " for domæne " + coreData.getDomain());
				return true;
			}
			else if (e.getCpr().length() != 10) {
				log.warn("Fjernet entry for Person med ugyldigt CPR nummer: " + e.getUuid() + " for domæne " + coreData.getDomain());
				return true;
			}
			
			try {
				Long.parseLong(e.getCpr());
			}
			catch (NumberFormatException ex) {
				log.warn("Fjernet entry for Person med ugyldigt CPR nummer. Indeholder tegn, som ikke er tal: " + e.getUuid() + " / " + e.getCpr() + " for domæne " + coreData.getDomain());
				return true;
			}
			
			return false;
		});

		for (CoreDataEntry entry : entries) {
			// copy domain to each entry - we need it for the entity identifier and for comparison
			entry.setDomain(coreData.getDomain());

			if (entry.getUuid() == null || entry.getUuid().length() != 36) {
				return "Person med ugyldigt UUID: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
			else if (!StringUtils.hasLength(entry.getName())) {
				return "Person uden navn: " + entry.getUuid() + " for domæne " + coreData.getDomain();
			}
		}

		return null;
	}

	private Person createPerson(CoreDataEntry coreDataEntry, Domain domain, HashMap<String, Domain> subDomainMap) {
		Person person = new Person();
		person.setSamaccountName(coreDataEntry.getLowerSamAccountName());
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
		
		if (domain.isNonNsis()) {
			; // for any user that comes from a non-nsis domain, never set the allowed/ordered flags
		}
		else {
			person.setNsisAllowed(coreDataEntry.isNsisAllowed());
		}

		person.setUuid(coreDataEntry.getUuid());
		person.setAttributes(coreDataEntry.getAttributes());
		person.setExpireTimestamp(coreDataEntry.getExpireTimestamp() != null ? LocalDateTime.parse(coreDataEntry.getExpireTimestamp(), formatter) : null);
		person.setDepartment(coreDataEntry.getDepartment());
		person.setTransferToNemlogin(coreDataEntry.isTransferToNemlogin());
		person.setPrivateMitId(coreDataEntry.isPrivateMitId());
		person.setQualifiedSignature(coreDataEntry.isQualifiedSignature());
		person.setEan(coreDataEntry.getEan());
		
		if (person.getExpireTimestamp() != null && person.getExpireTimestamp().isBefore(LocalDateTime.now())) {
			person.setLockedExpired(true);
		}
		
		// set subDomain if specified, otherwise use normal domain
		if (StringUtils.hasLength(coreDataEntry.getSubDomain())) {
			Domain coreDataEntryDomain = subDomainMap.get(coreDataEntry.getSubDomain());
			if (coreDataEntryDomain == null) {
				// will not actually happen, as we validate for this before we reach this point... this would be a coding error if this happens
				throw new RuntimeException("unknown subdomain: " + coreDataEntry.getSubDomain());
			}

			person.setDomain(coreDataEntryDomain);
		}
		else {
			person.setDomain(domain);
		}
		
		if (commonConfiguration.getMitIdErhverv().isEnabled()) {
			person.setExternalNemloginUserUuid(coreDataEntry.getExternalNemloginUserUuid());
		}
		
		if (configuration.getCoreData().isTrustedEmployeeEnabled()) {
			person.setTrustedEmployee(coreDataEntry.isTrustedEmployee());
		}

		person.setRobot(coreDataEntry.isRobot());

		if (configuration.getCoreData().isRoleApiEnabled()) {
			if (coreDataEntry.getRoles() != null) {
				for (String role : coreDataEntry.getRoles()) {
					CoreDataApi.PersonRoles pRole = null;
					
					try {
						pRole = CoreDataApi.PersonRoles.valueOf(role);
					}
					catch (Exception ex) {
						throw new RuntimeException("Unknown role: " + role);
					}

					switch (pRole) {
						case ADMIN:
							person.setAdmin(true);
							break;
						case KODEVISER_ADMIN:
							person.setKodeviserAdmin(true);
							break;
						case REGISTRANT:
							if (commonConfiguration.getCustomer().isEnableRegistrant()) {
								person.setRegistrant(true);
							}
							break;
						case SUPPORTER:
							person.setSupporter(new Supporter());
							person.getSupporter().setDomain(person.getDomain());
							person.getSupporter().setPerson(person);
							break;
						case TU_ADMIN:
							person.setServiceProviderAdmin(true);
							break;
						case USER_ADMIN:
							person.setUserAdmin(true);
							break;
						case PASSWORD_RESET_ADMIN:
							person.setPasswordResetAdmin(true);
							break;
						case STUDENT_PASSWORD_RESET_ADMIN:
							person.setInstitutionStudentPasswordAdmin(true);
							break;
					}
				}
			}
		}

		return person;
	}

	private boolean updatePerson(CoreDataEntry coreDataEntry, Domain domain, Person person, HashMap<String, Domain> subDomainMap) {
		boolean modified = false;

		// a person can be UPDATED to a ROBOT, if and only if, the person does not have an nsis-level or is created in NemLog-in
		if (coreDataEntry.isRobot()) {
			if (person.isNsisAllowed() || person.isTransferToNemlogin()) {
				coreDataEntry.setRobot(false);
			}
		}

		// a person that is a ROBOT can never stop being a ROBOT. Then the person needs to be deleted and re-created
		if (person.isRobot() && !coreDataEntry.isRobot()) {
			coreDataEntry.setRobot(true);
		}

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
		
		// the UUID is just an attribute, and can be modified
		if (!Objects.equals(person.getUuid(), coreDataEntry.getUuid())) {
			person.setUuid(coreDataEntry.getUuid());
			modified = true;
		}

		if (!coreDataEntry.isNsisAllowed() && !Objects.equals(person.getName(), coreDataEntry.getName())) {
			person.setName(coreDataEntry.getName());
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
		
		if (commonConfiguration.getMitIdErhverv().isEnabled()) {
			if (!Objects.equals(person.getExternalNemloginUserUuid(), coreDataEntry.getExternalNemloginUserUuid())) {
				person.setExternalNemloginUserUuid(coreDataEntry.getExternalNemloginUserUuid());
				modified = true;
			}
		}
		else if (person.getExternalNemloginUserUuid() != null) {
			// if the feature has been disabled, some persons might have a value set, so remove that value
			person.setExternalNemloginUserUuid(null);
			modified = true;
		}

		String dateToCompare = (person.getExpireTimestamp() == null) ? null : person.getExpireTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		if (!Objects.equals(dateToCompare, coreDataEntry.getExpireTimestamp())) {
			person.setExpireTimestamp((coreDataEntry.getExpireTimestamp() != null) ? LocalDateTime.parse(coreDataEntry.getExpireTimestamp(), formatter) : null);
			
			if (person.getExpireTimestamp() != null && person.getExpireTimestamp().isBefore(LocalDateTime.now())) {
				person.setLockedExpired(true);
			}
			else {
				person.setLockedExpired(false);
			}

			modified = true;
		}

		if (person.isLockedDataset()) {
			person.setLockedDataset(false);
			person.setApprovedConditions(false);
			person.setApprovedConditionsTts(null);
			person.setNsisLevel(NSISLevel.NONE);

			// make sure any messages in the queue about being locked are removed (just in case this user was locked previously, and the message has not actually
			// been processed yet - no reason to send the message, as it was a mistake that the user was locked
			messageQueueService.dequeue(person.getCpr(), person.getEmail(), EmailTemplateType.PERSON_DEACTIVATED_CORE_DATA);
			messageQueueService.dequeue(person.getCpr(), person.getEmail(), EmailTemplateType.FULL_SERVICE_IDP_REMOVED);
			
			auditLogger.addedToDataset(person);
			modified = true;
		}

		// update NsisAllowed/NsisOrdered fields
		modified |= setNsisState(person, coreDataEntry.isNsisAllowed());

		if (!mapEquals(person.getAttributes(), coreDataEntry.getAttributes())) {
			person.setAttributes(coreDataEntry.getAttributes());
			modified = true;
		}
		
		if (coreDataEntry.isTransferToNemlogin() != person.isTransferToNemlogin()) {
			person.setTransferToNemlogin(coreDataEntry.isTransferToNemlogin());
			
			auditLogger.transferToNemloginChanged(person, person.isTransferToNemlogin());

			modified = true;
		}

		if (coreDataEntry.isRobot() != person.isRobot()) {
			person.setRobot(coreDataEntry.isRobot());
			modified = true;
		}

		if (coreDataEntry.isPrivateMitId() != person.isPrivateMitId()) {
			person.setPrivateMitId(coreDataEntry.isPrivateMitId());

			auditLogger.allowPrivateMitIdChanged(person, person.isPrivateMitId());

			modified = true;
		}

		if (coreDataEntry.isQualifiedSignature() != person.isQualifiedSignature()) {
			person.setQualifiedSignature(coreDataEntry.isQualifiedSignature());

			auditLogger.allowQualifiedSignatureChanged(person, person.isQualifiedSignature());

			modified = true;
		}
		
		if (!Objects.equals(person.getDepartment(), coreDataEntry.getDepartment())) {
			person.setDepartment(coreDataEntry.getDepartment());
			modified = true;
		}

		if (!Objects.equals(person.getEan(), coreDataEntry.getEan())) {
			person.setEan(coreDataEntry.getEan());
			modified = true;
		}

		if (configuration.getCoreData().isTrustedEmployeeEnabled()) {
			if (person.isTrustedEmployee() != coreDataEntry.isTrustedEmployee()) {
				person.setTrustedEmployee(coreDataEntry.isTrustedEmployee());
				modified = true;
			}
		}

		if (configuration.getCoreData().isRoleApiEnabled()) {
			Set<CoreDataApi.PersonRoles> pRoles = new HashSet<>();

			if (coreDataEntry.getRoles() != null) {
				for (String role : coreDataEntry.getRoles()) {
					CoreDataApi.PersonRoles pRole = null;
					
					try {
						pRole = CoreDataApi.PersonRoles.valueOf(role);
						pRoles.add(pRole);
					}
					catch (Exception ex) {
						throw new RuntimeException("Unknown role: " + role);
					}
				}
			}

			// add roles
			for (CoreDataApi.PersonRoles pRole : pRoles) {
				switch (pRole) {
					case ADMIN:
						if (!person.isAdmin()) {
							person.setAdmin(true);
							modified = true;
						}
						break;
					case KODEVISER_ADMIN:
						if (!person.isKodeviserAdmin()) {
							person.setKodeviserAdmin(true);
							modified = true;
						}
						break;
					case REGISTRANT:
						if (commonConfiguration.getCustomer().isEnableRegistrant()) {
							if (!person.isRegistrant()) {
								person.setRegistrant(true);
								modified = true;
							}
						}
						break;
					case SUPPORTER:
						if (!person.isSupporter()) {
							person.setSupporter(new Supporter());
							person.getSupporter().setDomain(person.getDomain());
							person.getSupporter().setPerson(person);
							modified = true;
						}
						break;
					case TU_ADMIN:
						if (!person.isServiceProviderAdmin()) {
							person.setServiceProviderAdmin(true);
							modified = true;
						}
						break;
					case USER_ADMIN:
						if (!person.isAdmin()) {
							person.setUserAdmin(true);
							modified = true;
						}
						break;
					case PASSWORD_RESET_ADMIN:
						if (!person.isPasswordResetAdmin()) {
							person.setPasswordResetAdmin(true);
							modified = true;
						}
						break;
					case STUDENT_PASSWORD_RESET_ADMIN:
						if (!person.isInstitutionStudentPasswordAdmin()) {
							person.setInstitutionStudentPasswordAdmin(true);
							modified = true;
						}
						break;
				}
			}
			
			// remove roles

			if (person.isPasswordResetAdmin() && !pRoles.contains(CoreDataApi.PersonRoles.PASSWORD_RESET_ADMIN)) {
				person.setPasswordResetAdmin(false);
				modified = true;
			}

			if (person.isInstitutionStudentPasswordAdmin() && !pRoles.contains(CoreDataApi.PersonRoles.STUDENT_PASSWORD_RESET_ADMIN)) {
				person.setInstitutionStudentPasswordAdmin(false);
				modified = true;
			}

			if (person.isAdmin() && !pRoles.contains(CoreDataApi.PersonRoles.ADMIN)) {
				person.setAdmin(false);
				modified = true;
			}
			
			if (person.isKodeviserAdmin() && !pRoles.contains(CoreDataApi.PersonRoles.KODEVISER_ADMIN)) {
				person.setKodeviserAdmin(false);
				modified = true;
			}
			
			if (person.isRegistrant() && !pRoles.contains(CoreDataApi.PersonRoles.REGISTRANT)) {
				person.setRegistrant(false);
				modified = true;
			}
			
			if (person.isServiceProviderAdmin() && !pRoles.contains(CoreDataApi.PersonRoles.TU_ADMIN)) {
				person.setServiceProviderAdmin(false);
				modified = true;
			}
			
			if (person.isUserAdmin() && !pRoles.contains(CoreDataApi.PersonRoles.USER_ADMIN)) {
				person.setUserAdmin(false);
				modified = true;
			}
			
			if (person.isSupporter() && !pRoles.contains(CoreDataApi.PersonRoles.SUPPORTER)) {
				person.setSupporter(null);
				modified = true;
			}
		}

		return modified;
	}
	
	private boolean mapEquals(Map<String, String> map1, Map<String, String> map2) {

		// basic null checking
		if (map1 == null && map2 == null) {
			return true;
		}
		else if (map1 == null && map2 != null) {
			return false;
		}
		else if (map1 != null && map2 == null) {
			return false;
		}

		// size check
		if (map1.keySet().size() != map2.keySet().size()) {
			return false;
		}
		
		// same size, so pull keyset from one map, and values from both maps and compare directly
		for (String key : map1.keySet()) {
			String value1 = map1.get(key);
			String value2 = map2.get(key);
			
			if (!Objects.equals(value1, value2)) {
				return false;
			}
		}
		
		return true;
	}

	@Transactional
	public void loadFullKombitJfr(CoreDataFullJfr coreData) {
		List<Person> persons = personService.getByDomain(coreData.getDomain(), true);

		Map<String, Person> personMapSAMAccountName = persons.stream().filter(p -> StringUtils.hasLength(p.getLowerSamAccountName())).collect(Collectors.toMap(Person::getLowerSamAccountName, Function.identity()));
		Map<String, Person> personMapAzureId = persons.stream().filter(p -> p.getAzureId() != null).collect(Collectors.toMap(Person::getAzureId, Function.identity()));
		Map<String, Person> personMap = persons.stream().filter(p -> p.getUuid() != null).collect(Collectors.toMap(person -> person.getUuid() + (person.getLowerSamAccountName() != null ? person.getLowerSamAccountName() : "<null>"), Function.identity()));

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
					person = personMap.get(entry.getUuid() + (entry.getLowerSamAccountName() != null ? entry.getLowerSamAccountName() : "<null>"));
				}
			}
			else {
				person = personMapSAMAccountName.get(entry.getLowerSamAccountName().toLowerCase());
			}

			if (person == null) {
				log.debug("Got person that does not exist in OS2faktor yet: " + (entry.getLowerSamAccountName() != null ? entry.getLowerSamAccountName() : "<null>") + " (" + (entry.getUuid() != null ? entry.getUuid() : "<null>") + ")");
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
				log.info("Registering JFR changes on " + person.getId());
				personService.save(person);
			}
		}

		Map<String, CoreDataFullJfrEntry> coreDataMapSAMAccountName = coreData.getEntryList().stream().filter(entry -> StringUtils.hasLength(entry.getLowerSamAccountName())).collect(Collectors.toMap(CoreDataFullJfrEntry::getLowerSamAccountName, Function.identity()));
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
		Domain domain = domainService.getByName(coreData.getDomain());
		if (domain == null) {
			log.error("loadDeltaKombitJfr - unknown domain: " + coreData.getDomain());
			return;
		}

		// add/update case
		for (CoreDataDeltaJfrEntry entry : coreData.getEntryList()) {
			List<Person> persons = personService.getBySamaccountNameAndDomain(entry.getSamAccountName(), domain);
			if (persons == null || persons.size() == 0) {
				log.warn("loadDeltaKombitJfr - unknown person: " + entry.getSamAccountName());
				continue;
			}
			
			// not sure why sAMAccountName does not have a unique constraint, the API enforces uniqueness when loading data
			Person person = persons.get(0);
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

	@Transactional
	public boolean setForceChangePassword(CoreDataForceChangePassword coreData) {
		List<Person> persons = personService.getByDomain(coreData.getDomain(), true);

		Optional<Person> optionalPerson = persons.stream()
				.filter(person -> Objects.equals(coreData.getSamAccountName(), person.getSamaccountName()))
				.findAny();

		if (optionalPerson.isEmpty()) {
			return false;
		}

		Person person = optionalPerson.get();
		person.setForceChangePassword(true);
		personService.save(person);
		return true;
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

				if (setNsisState(domainPerson, shouldNsisBeAllowed)) {
					toBeSaved.add(domainPerson);
				}
			}

			personService.saveAll(toBeSaved);
		}
	}
	
	@Transactional(rollbackFor = Exception.class)
	public void loadTransferToNemlogin(CoreDataNemLoginAllowed transferToNemlogin) {
		if (transferToNemlogin.getNemLoginUserUuids() != null) {
			Domain domain = domainService.getByName(transferToNemlogin.getDomain());
			if (domain == null) {
				throw new IllegalAccessError("Unknown domain: " + transferToNemlogin.getDomain());
			}

			List<Person> domainPeople = personService.getByDomainNotLockedByDataset(domain, true);

			List<Person> toBeSaved = new ArrayList<>();
			for (Person domainPerson : domainPeople) {
				boolean shouldTransferToNemlogin = transferToNemlogin.getNemLoginUserUuids().contains(domainPerson.getUuid());
				boolean currentTransferToNemlogin = domainPerson.isTransferToNemlogin();

				if (shouldTransferToNemlogin != currentTransferToNemlogin) {
					domainPerson.setTransferToNemlogin(shouldTransferToNemlogin);
					toBeSaved.add(domainPerson);
					auditLogger.transferToNemloginChanged(domainPerson, shouldTransferToNemlogin);
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
				.filter(p -> p.getLowerSamAccountName() != null)
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
				Person personToAdd = personMap.get(member.toLowerCase());

				// TODO: we can remove this code sometime in the future, when we know that everyone has upgraded to using sAMAccountName in CoreData
				if (personToAdd == null) {
					for (Person person : allPersons) {
						if (Objects.equals(person.getUuid(), member)) {
							personToAdd = person;
							break;
						}
					}
				}

				if (personToAdd == null) {
					log.warn("Group payload contains sAMAccountName of person that does not exist:" + member);
					continue;
				}

				// if the person is not already a member, then add
				final Person fPersonToAdd = personToAdd;
				if (!group.getMembers().stream().anyMatch(p -> p.getId() == fPersonToAdd.getId())) {
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
					// TODO: can remove the UUID check in the future (once everyone is running latest CoreData)
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
	
	public void loadKombitAttributesFull(CoreDataKombitAttributesLoad kombitAttributes) {
		List<Person> updatedPersons = new ArrayList<>();

		Domain domain = domainService.getByName(kombitAttributes.getDomain());
		if (domain == null) {
			throw new IllegalArgumentException("Ukendt domæne: " + kombitAttributes.getDomain());
		}
		
		// Get people from the database, active only (not locked by dataset)
		List<Person> personList = personService.getByDomainNotLockedByDataset(kombitAttributes.getDomain(), true);
		Map<String, Person> personMap = personList.stream().collect(Collectors.toMap(Person::getLowerSamAccountName, Function.identity()));

		// Validate input
		String result = validateInput(kombitAttributes, personList);
		if (result != null) {
			throw new IllegalArgumentException(result);
		}
		
		// Go through the received list of entries, check if there's a matching person object, then update/create
		for (CoreDataKombitAttributeEntry attributeEntry : kombitAttributes.getEntryList()) {
			Person person = personMap.get(attributeEntry.getSamAccountName().toLowerCase());

			if (person != null) {

				// map equality works on element equality (which works because they are Strings), and the order is not relevant (equality still works)
				if (!Objects.equals(person.getKombitAttributes(), attributeEntry.getKombitAttributes())) {
					person.setKombitAttributes(attributeEntry.getKombitAttributes());
					updatedPersons.add(person);
				}
			}
		}
		
		// Save all the updated people
		if (updatedPersons.size() > 0) {
			personService.saveAll(updatedPersons);
			log.info(updatedPersons.size() + " persons were updated with new KOMBIT attributes");
		}
	}

	public void loadKombitAttributesSingle(String domainName, CoreDataKombitAttributeEntry kombitAttribute) {
		Domain domain = domainService.getByName(domainName);
		if (domain == null) {
			throw new IllegalArgumentException("Ukendt domæne: " + domainName);
		}
		
		if (domain.getParent() != null) {
			throw new IllegalArgumentException("Angivne domæne (" + domainName + ") er et sub-domæne");
		}
		
		if (!StringUtils.hasLength(kombitAttribute.getSamAccountName())) {
			throw new IllegalArgumentException("Person mangler sAMAccountName for domæne " + domainName);
		}
		
		String sAMAccountName = kombitAttribute.getSamAccountName().toLowerCase();

		List<Person> persons = personService.getBySamaccountNameAndDomain(sAMAccountName, domain);
		if (persons == null || persons.size() == 0) {
			throw new IllegalArgumentException("No person found with sAMAccountName: " + sAMAccountName);
		}
		
		Person person = persons.stream().filter(p -> !p.isLocked()).findFirst().orElse(null);
		if (person == null) {
			throw new IllegalArgumentException("No active person found with sAMAccountName: " + sAMAccountName);
		}

		// Go through the received list of entries, check if there's a matching person object, then update/create
		if (!Objects.equals(person.getKombitAttributes(), kombitAttribute.getKombitAttributes())) {
			person.setKombitAttributes(kombitAttribute.getKombitAttributes());
			personService.save(person);
		}
	}
	
	// update the nsisAllowed and/or the nsisOrdered fields depending on input, ensuring correct logging and email sending if needed
	private boolean setNsisState(Person person, boolean newNsisState) {
		boolean modified = false;

		// for any user that comes from a non-nsis domain, never set the allowed/ordered flags
		if (person.getDomain().isNonNsis()) {
			if (person.isNsisAllowed()) {
				person.setNsisAllowed(false);

				return true;
			}
			
			return false;
		}
		
		if (newNsisState != person.isNsisAllowed()) {
			if (newNsisState == true) {
				person.setNsisAllowed(true);
				person.setNsisLevel(NSISLevel.NONE);
				
				EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.NSIS_ALLOWED);
				for (EmailTemplateChild child : emailTemplate.getChildren()) {
					if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
						String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
						message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
						emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, false);
					}
				}

				if (commonConfiguration.getFullServiceIdP().isEnabled()) {
					emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.FULL_SERVICE_IDP_ASSIGNED);
					for (EmailTemplateChild child : emailTemplate.getChildren()) {
						if (child.getDomain().getId() == person.getDomain().getId()) {
							String message = emailTemplateService.safeReplaceEverything(child.getMessage(), person);

							emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, false);
						}
					}
				}
			}
			else {
				person.setNsisAllowed(false);
				person.setNsisLevel(NSISLevel.NONE);
				
				// if the person is not locked, we should send a deactivate mail (note that when a person is locked, one is also send,
				// so we do this to avoid sending it twice
				if (!person.isLockedDataset()) {
					sendDeactivateEmail(person);
				}
			}

			auditLogger.nsisAllowedChanged(person, person.isNsisAllowed());

			modified = true;
		}
		
		return modified;
	}

	public CoreDataEntryLight getByCpr(String userId, String domainName) {
		Domain domain = domainService.getByName(domainName);
		if (domain == null) {
			return null;
		}

		List<Person> persons = personService.getBySamaccountNameAndDomain(userId, domain);
		if (persons == null || persons.size() == 0) {
			return null;
		}
		
		Person person = persons.stream().filter(p -> !p.isLocked()).findFirst().orElse(null);
		if (person == null) {
			return null;
		}

		return new CoreDataEntryLight(person);
	}

	public void addPersonToGroup(Domain domain, String groupUuid, String sAMAccountName) {
		List<Person> persons = personService.getBySamaccountNameAndDomain(sAMAccountName, domain);
		if (persons == null || persons.size() == 0) {
			throw new IllegalArgumentException("No person found with sAMAccountName: " + sAMAccountName);
		}
		
		Person person = persons.stream().filter(p -> !p.isLocked()).findFirst().orElse(null);
		if (person == null) {
			throw new IllegalArgumentException("No active person found with sAMAccountName: " + sAMAccountName);
		}
		
		Group group = groupService.getByUUID(groupUuid);
		if (group == null) {
			throw new IllegalArgumentException("No group found with uuid: " + groupUuid);
		}
		
		// if the person is not already a member, then add
		if (!group.getMembers().stream().anyMatch(p -> p.getId() == person.getId())) {
			log.info("Adding user to group (" + group.getName() + ") : " + PersonService.getUsername(person));
			PersonGroupMapping pgm = new PersonGroupMapping(person, group);
			group.getMemberMapping().add(pgm);
			
			groupService.save(group);
		}
	}

	public void deletePersonFromGroup(Domain domain, String groupUuid, String sAMAccountName) {
		List<Person> persons = personService.getBySamaccountNameAndDomain(sAMAccountName, domain);
		if (persons == null || persons.size() == 0) {
			throw new IllegalArgumentException("No person found with sAMAccountName: " + sAMAccountName);
		}
		
		Person person = persons.stream().filter(p -> !p.isLocked()).findFirst().orElse(null);
		if (person == null) {
			throw new IllegalArgumentException("No active person found with sAMAccountName: " + sAMAccountName);
		}
		
		Group group = groupService.getByUUID(groupUuid);
		if (group == null) {
			throw new IllegalArgumentException("No group found with uuid: " + groupUuid);
		}
		
		if (group.getMembers().stream().noneMatch(p -> p.getSamaccountName().equals(sAMAccountName))) {
			throw new IllegalArgumentException("Person with sAMAccountName: " + sAMAccountName + " is not a member of group: " +  groupUuid);
		}
		
		group.getMemberMapping().removeIf(m -> m.getPerson().getSamaccountName().equals(sAMAccountName));
		
		groupService.save(group);
	}

	public CoreDataExtendedLookup lookupExtended(Domain domain, String userId) {
		List<Person> persons = personService.getBySamaccountNameAndDomain(userId, domain);
		if (persons == null || persons.size() != 1) {
			return null;
		}
		
		CoreDataExtendedLookup lookup = new CoreDataExtendedLookup();
		Person person = persons.get(0);
		
		lookup.setOs2faktorActiveCorporateId(person.hasActivatedNSISUser());
		lookup.setOs2faktorBadPassword(person.isBadPassword());
		lookup.setOs2faktorLocked(person.isLocked());
		
		if (StringUtils.hasText(person.getNemloginUserUuid())) {
			boolean mitIdDown = false;
			
			try {
				FullEmployee fullEmployee = nemLoginService.getFullEmployee(person.getNemloginUserUuid());
				if (fullEmployee != null && fullEmployee.getIdentityProfile() != null) {
					lookup.setMitIdErhvervStatus(fullEmployee.getIdentityProfile().getStatus());
					lookup.setMitIdErhvervRid(fullEmployee.getIdentityProfile().getRid());
					lookup.setMitIdErhvervUuid(fullEmployee.getUuid());
				}
			}
			catch (Exception ex) {
				mitIdDown = true;
				log.warn("Failed to lookup MitID Erhverv data for " + person.getSamaccountName(), ex);
			}

			MitidErhvervCache cache = mitIdErhvervCacheService.findByUuid(person.getNemloginUserUuid());
			if (cache != null) {
				if (mitIdDown) {
					lookup.setMitIdErhvervStatus(cache.getStatus());
					lookup.setMitIdErhvervRid(cache.getRid());
					lookup.setMitIdErhvervUuid(cache.getUuid());					
				}
				
				lookup.setMitIdErhvervQualifiedSignature(cache.isQualifiedSignature());
				lookup.setMitIdErhvervPrivateMitID(cache.isMitidPrivatCredential());
			}
		}

		return lookup;
	}
}
