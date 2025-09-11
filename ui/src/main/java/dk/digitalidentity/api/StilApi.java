package dk.digitalidentity.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.StilData;
import dk.digitalidentity.api.dto.StilGroup;
import dk.digitalidentity.api.dto.StilPerson;
import dk.digitalidentity.api.dto.StilPersonType;
import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.modules.StilPersonCreationRoleSetting;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.mapping.SchoolRoleSchoolClassMapping;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SchoolClassService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class StilApi {

	@Autowired
	private SchoolClassService schoolClassService;

	@Autowired
	private PersonService personService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private DomainService domainService;

	@PostMapping("/api/stil/full")
	public ResponseEntity<?> fullLoad(@RequestBody StilData stilData) {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return ResponseEntity.badRequest().body("Feature not enabled");
		}

		log.info("WS17 full sync trace: before lookup up domain");
		
		Domain domain = domainService.getByName(stilData.getDomainName());
		if (domain == null) {
			return ResponseEntity.badRequest().body("Unknown domain: " + stilData.getDomainName());
		}

		// whenever we do lookups, we need to find persons inside all the domains
		List<Domain> domainAndSubdomains = new ArrayList<>();
		domainAndSubdomains.add(domain);
		domainAndSubdomains.addAll(domain.getChildDomains());

		log.info("WS17 full sync trace: before getting all school classes");

		List<SchoolClass> classes = schoolClassService.getAll();
		List<Person> persons = personService.getByDomain(domain, true);
		
		log.info("WS17 full sync trace: before getting all persons from domain");

		Map<String, List<Person>> personMap = persons.stream().collect(Collectors.groupingBy(Person::getCpr));

		log.info("WS17 full sync trace: before updating all classes");

		// step 1 - make sure all classes exists and are updated
		Map<String, SchoolClass> classMap = createAndUpdateClasses(stilData, classes);

		// step 2 - make sure all roles are assigned to persons
		List<String> personsWithRoles = new ArrayList<>();
		
		log.info("WS17 full sync trace: before updating all school roles");

		createAndUpdatePersonSchoolRoles(classMap, stilData, domain, personMap, personsWithRoles);
		
		log.info("WS17 full sync trace: before cleaning old school roles");

		clearPersonSchoolRoles(domainAndSubdomains, personsWithRoles);

		log.info("WS17 full sync trace: before deleting non-existing classes");

		// step 3 - remove unused classes
		deleteNonExistingClasses(stilData, classes);

		log.info("WS17 full sync trace: done");

		return ResponseEntity.ok().build();
	}

	private void clearPersonSchoolRoles(List<Domain> domainAndSubdomains, List<String> personsWithRoles) {
		List<Person> peopleToClearSchoolRoles = personService.getBySchoolRolesNotEmptyAndDomainIn(domainAndSubdomains).stream()
				.filter(p -> !personsWithRoles.contains(p.getCpr()))
				.collect(Collectors.toList());

		for (Person person : peopleToClearSchoolRoles) {
			person.getSchoolRoles().clear();
		}

		personService.saveAll(peopleToClearSchoolRoles);
	}

	private void createAndUpdatePersonSchoolRoles(Map<String, SchoolClass> classMap, StilData stilData, Domain domain, Map<String, List<Person>> personMap, List<String> personsWithRoles) {
		List<StilPersonCreationRoleSetting> stilCreateSettings = commonConfiguration.getStilPersonCreation().getRoleSettings();
		
		log.info("WS17 full sync trace - before entering loop of " + stilData.getPeople().size() + " StilPersons");

		long globalStart = System.currentTimeMillis();
		long saveTotal = 0, updateTotal = 0;
		long fullAddCounter = 0, updateCounter = 0, saveCallCounter = 0, createdSchoolRolesCounter = 0, removeClassCounter = 0, removeSchoolRolesCounter = 0;

		List<Person> toSave = new ArrayList<>();
		for (StilPerson currentStilPerson : stilData.getPeople()) {
			String cpr = currentStilPerson.getCpr();
			if (personsWithRoles.contains(cpr)) {
				continue;
			}

			personsWithRoles.add(cpr);

			List<Person> peopleWithThisCprAndDomain = personMap.get(cpr);
			
			if (peopleWithThisCprAndDomain == null && !commonConfiguration.getStilPersonCreation().isEnabled()) {
				log.debug("Person with cpr " + PersonService.maskCpr(cpr) + " does not exist, but was received as a StilPerson from the STIL integration.");
				continue;
			}

			// grab ALL the stilData for this CPR and handle it together
			List<StilPerson> stilPeopleWithThisCpr = stilData.getPeople().stream().filter(s -> s.getCpr().equals(cpr)).collect(Collectors.toList());

			// create
			boolean create = false;
			if ((peopleWithThisCprAndDomain == null || peopleWithThisCprAndDomain.isEmpty()) && commonConfiguration.getStilPersonCreation().isEnabled() && (commonConfiguration.getStilPersonCreation().isCreateEmployees() || commonConfiguration.getStilPersonCreation().isCreateStudents())) {
				if (stilPeopleWithThisCpr.stream().anyMatch(s -> s.getType().equals(StilPersonType.EMPLOYEE) || s.getType().equals(StilPersonType.EXTERN)) && commonConfiguration.getStilPersonCreation().isCreateEmployees()) {
					create = true;
				}
				if (stilPeopleWithThisCpr.stream().anyMatch(s -> s.getType().equals(StilPersonType.STUDENT)) && commonConfiguration.getStilPersonCreation().isCreateStudents()) {
					create = true;
				}
			}
			
			if (create) {
				Person person = new Person();
				person.setUuid(UUID.randomUUID().toString());
				person.setCpr(cpr);
				person.setName(currentStilPerson.getName());
				person.setSamaccountName(currentStilPerson.getUserId());
				person.setNsisLevel(NSISLevel.NONE);
				person.setDomain(domain);
				person.setNsisAllowed(isNsisAllowed(stilPeopleWithThisCpr, stilCreateSettings));
				person.setTransferToNemlogin(isTransferToNemLogin(stilPeopleWithThisCpr, stilCreateSettings));
				person.setSchoolRoles(new ArrayList<>());
				
				person = personService.save(person);
				saveCallCounter++;
				peopleWithThisCprAndDomain = new ArrayList<>();
				peopleWithThisCprAndDomain.add(person);
			}

			// update school roles and stuff (also happens on create scenario)
			for (Person person : peopleWithThisCprAndDomain) {
				if (person.getSchoolRoles().isEmpty() && stilPeopleWithThisCpr.isEmpty()) {
					; // do nothing
				}
				else if (person.getSchoolRoles().isEmpty() && !stilPeopleWithThisCpr.isEmpty()) {
					fullAddCounter++;

					// all schoolRoles are new roles
					for (StilPerson stilPerson : stilPeopleWithThisCpr) {
						createdSchoolRolesCounter++;
						SchoolRole newRole = createSchoolRole(classMap, person, stilPerson);
						person.getSchoolRoles().add(newRole);
					}

					saveCallCounter++;
					personService.save(person);
				}
				else {
					updateCounter++;
					boolean changes = false;

					for (StilPerson stilPerson : stilPeopleWithThisCpr) {
						SchoolRole match = person.getSchoolRoles().stream()
								.filter(s -> Objects.equals(s.getInstitutionId(), stilPerson.getInstitutionNumber()) && Objects.equals(s.getRole(), stilPerson.getRole()))
								.findAny()
								.orElse(null);

						if (match == null) {
							createdSchoolRolesCounter++;
							SchoolRole newRole = createSchoolRole(classMap, person, stilPerson);
							person.getSchoolRoles().add(newRole);

							log.info("WS17 full sync trace - adding schoolRole " + stilPerson.getRole() + "/" + stilPerson.getInstitutionName() + " to " + stilPerson.getCpr());
							
							changes = true;
						}
						else {

							// add groups/classes that are assigned in STIL but not here
							List<String> assignedGroups = match.getSchoolClasses().stream()
									.map(s -> s.getSchoolClass().getClassIdentifier())
									.collect(Collectors.toList());

							for (String groupId : stilPerson.getGroups()) {

								if (!assignedGroups.contains(groupId)) {
									SchoolClass schoolClass = classMap.get(groupId + ":" + stilPerson.getInstitutionNumber());
									if (schoolClass != null) {
										log.info("WS17 full sync trace - adding class " + (groupId + ":" + stilPerson.getInstitutionName()) + " to " + stilPerson.getCpr());
										changes = true;
										
										match.getSchoolClasses().add(new SchoolRoleSchoolClassMapping(match, schoolClass));
									}
									else {
										log.warn("Unable to find a class with identifier: " + groupId + " / " + stilPerson.getInstitutionName());
									}
								}
							}

							// remove groups/classes that are not assigned to this role in STIL anymore
							Iterator<SchoolRoleSchoolClassMapping> iterator = match.getSchoolClasses().iterator();
							while (iterator.hasNext()) {
								SchoolRoleSchoolClassMapping mapping = iterator.next();
								SchoolClass schoolClass = mapping.getSchoolClass();

								// the class ID should match the class/group on the stilPerson, and the institutionID should match
								String classMatch = stilPerson.getGroups().stream()
										.filter(g -> g.equals(schoolClass.getClassIdentifier()) &&
													 Objects.equals(stilPerson.getInstitutionNumber(), schoolClass.getInstitutionId()))
										.findAny()
										.orElse(null);

								if (classMatch == null) {
									log.info("WS17 full sync trace - removing class " + schoolClass.getClassIdentifier() + "/" + match.getInstitutionName() + " from " + stilPerson.getCpr());
									
									removeClassCounter++;
									changes = true;
									iterator.remove();
								}
							}
						}
					}

					// remove schoolRoles that are not in STIL anymore
					Iterator<SchoolRole> iterator = person.getSchoolRoles().iterator();
					while (iterator.hasNext()) {
						SchoolRole role = iterator.next();
						StilPerson match = stilPeopleWithThisCpr.stream().filter(s -> s.getInstitutionNumber().equals(role.getInstitutionId()) && s.getRole().equals(role.getRole())).findAny().orElse(null);

						if (match == null) {
							log.info("WS17 full sync trace - removing schoolRole " + role.getRole() + "/" + role.getInstitutionName() + " from " + person.getCpr());
							
							removeSchoolRolesCounter++;
							changes = true;
							iterator.remove();
							break;
						}
					}
					
					// update person fields regarding NSIS/MitID Erhverv IF (and only if) the personCreate feature is enabled
					if (!create && commonConfiguration.getStilPersonCreation().isEnabled()) {
						boolean nsisAllowed = isNsisAllowed(stilPeopleWithThisCpr, stilCreateSettings);
						if (!Objects.equals(person.isNsisAllowed(), nsisAllowed)) {
							person.setNsisAllowed(nsisAllowed);
							changes = true;
						}
						
						boolean transferToNemLogin = isTransferToNemLogin(stilPeopleWithThisCpr, stilCreateSettings);
						if (!Objects.equals(person.isTransferToNemlogin(), transferToNemLogin)) {
							person.setTransferToNemlogin(transferToNemLogin);
							changes = true;
						}
					}

					if (changes) {
						toSave.add(person);
					}
				}
			}
		}
		
		updateTotal = System.currentTimeMillis() - globalStart;
				
		if (toSave.size() > 0) {
			log.info("WS 17 full sync trace - before save of " + toSave.size() + " persons");
			long saveStart = System.currentTimeMillis();
			personService.saveAll(toSave);
			saveTotal = (System.currentTimeMillis() - saveStart);
			log.info("WS 17 full sync trace - after save");
		}
		
		long runTotal = System.currentTimeMillis() - globalStart;

		log.info("WS17 full sync trace - total time " + runTotal + "ms, with " + saveTotal + "ms on save() and " + updateTotal + "ms on update - with update split into ");
		log.info("WS17 full sync trace - counters: fullAddCounter=" + fullAddCounter + ", updateCounter=" + updateCounter + ", individualSaveCallCounter=" + saveCallCounter + ", createdSchoolRolesCounter=" + createdSchoolRolesCounter + ", removeClassCounter=" + removeClassCounter + ", removeSchoolRolesCounter=" + removeSchoolRolesCounter);
	}

	private boolean isNsisAllowed(List<StilPerson> stilPeopleWithThisCpr, List<StilPersonCreationRoleSetting> stilCreateSettings) {
		for (StilPerson person : stilPeopleWithThisCpr) {
			StilPersonCreationRoleSetting roleSetting = stilCreateSettings.stream().filter(s -> s.getRole().equals(person.getRole())).findFirst().orElse(null);
			if (roleSetting != null) {
				if (roleSetting.isNsisAllowed()) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	private boolean isTransferToNemLogin(List<StilPerson> stilPeopleWithThisCpr, List<StilPersonCreationRoleSetting> stilCreateSettings) {
		for (StilPerson person : stilPeopleWithThisCpr) {
			StilPersonCreationRoleSetting roleSetting = stilCreateSettings.stream().filter(s -> s.getRole().equals(person.getRole())).findFirst().orElse(null);
			if (roleSetting != null) {
				if (roleSetting.isTransferToNemLogin()) {
					return true;
				}
			}
		}
		
		return false;
	}

	private void deleteNonExistingClasses(StilData stilData, List<SchoolClass> classes) {
		List<String> groupIds = stilData.getStudentGroups().stream().map(s -> s.getId()).collect(Collectors.toList());
		List<SchoolClass> toBeDeleted = classes.stream().filter(c -> !groupIds.contains(c.getClassIdentifier())).collect(Collectors.toList());

		schoolClassService.deleteAll(toBeDeleted);
	}

	private Map<String, SchoolClass> createAndUpdateClasses(StilData stilData, List<SchoolClass> existingClasses) {
		Map<String, SchoolClass> map = existingClasses.stream().collect(Collectors.toMap(SchoolClass::uniqueId, Function.identity()));

		for (StilGroup group : stilData.getStudentGroups()) {
			SchoolClass match = existingClasses.stream()
					.filter(c -> c.getClassIdentifier().equals(group.getId()) && c.getInstitutionId().equals(group.getInstitutionNumber()))
					.findAny()
					.orElse(null);

			if (match == null) {
				SchoolClass newClass = new SchoolClass();
				newClass.setName(group.getName());
				newClass.setClassIdentifier(group.getId());
				newClass.setInstitutionId(group.getInstitutionNumber());
				newClass.setLevel(group.getLevel());
				newClass.setType(group.getType());

				newClass = schoolClassService.save(newClass);

				map.put(newClass.uniqueId(), newClass);
			}
			else {
				boolean changes = false;

				if (!Objects.equals(match.getName(), group.getName())) {
					match.setName(group.getName());
					changes = true;
				}

				if (!Objects.equals(match.getLevel(), group.getLevel())) {
					match.setLevel(group.getLevel());
					changes = true;
				}

				if (!Objects.equals(match.getType(), group.getType())) {
					match.setType(group.getType());
					changes = true;
				}

				if (changes) {
					schoolClassService.save(match);
				}
			}
		}
		
		return map;
	}

	private SchoolRole createSchoolRole(Map<String, SchoolClass> classMap, Person person, StilPerson stilPerson) {
		SchoolRole newRole = new SchoolRole();
		newRole.setInstitutionId(stilPerson.getInstitutionNumber());
		newRole.setInstitutionName(stilPerson.getInstitutionName());
		newRole.setPerson(person);
		newRole.setRole(stilPerson.getRole());
		newRole.setSchoolClasses(new ArrayList<>());

		for (String group : stilPerson.getGroups()) {
			SchoolClass schoolClass = classMap.get(group + ":" + stilPerson.getInstitutionNumber());
			if (schoolClass != null) {
				newRole.getSchoolClasses().add(new SchoolRoleSchoolClassMapping(newRole, schoolClass));
			}
			else {
				log.warn("Attempted to create a role for an unknown class: " + group + " / " + stilPerson.getInstitutionNumber());
			}
		}

		return newRole;
	}
}
