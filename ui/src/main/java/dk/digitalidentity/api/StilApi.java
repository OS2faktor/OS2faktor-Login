package dk.digitalidentity.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
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

		Domain domain = domainService.getByName(stilData.getDomainName());
		if (domain == null) {
			return ResponseEntity.badRequest().body("Unknown domain: " + stilData.getDomainName());
		}
		
		// whenever we do lookups, we need to find persons inside all the domains
		List<Domain> domainAndSubdomains = new ArrayList<>();
		domainAndSubdomains.add(domain);
		domainAndSubdomains.addAll(domain.getChildDomains());

		List<SchoolClass> classes = schoolClassService.getAll();
		List<Person> persons = personService.getByDomain(domain, true);
		Map<String, List<Person>> personMap = persons.stream().collect(Collectors.groupingBy(Person::getCpr));

		// step 1 - make sure all classes exists and are updated
		Map<String, SchoolClass> classMap = createAndUpdateClasses(stilData, domain, classes);

		// step 2 - make sure all roles are assigned to persons
		List<String> personsWithRoles = new ArrayList<>();
		createAndUpdatePersonSchoolRoles(classMap, stilData, domain, personMap, personsWithRoles);
		clearPersonSchoolRoles(domainAndSubdomains, personsWithRoles);

		// step 3 - remove unused classes
		deleteNonExistingClasses(stilData, classes);

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

		for (StilPerson currentStilPerson : stilData.getPeople()) {
			String cpr = currentStilPerson.getCpr();

			if (personsWithRoles.contains(cpr)) {
				continue;
			}
			personsWithRoles.add(cpr);

			List<Person> peopleWithThisCprAndDomain = personMap.get(cpr);
			if (peopleWithThisCprAndDomain == null) {
				// TODO: change to debug at some point, but for now let's spam our logs :)
				log.warn("Person with cpr " + PersonService.maskCpr(cpr) + " does not exist, but was received as a StilPerson from the STIL integration.");
				continue;
			}

			// grab ALL the stilData for this CPR and handle it together
			List<StilPerson> stilPeopleWithThisCpr = stilData.getPeople().stream().filter(s -> s.getCpr().equals(cpr)).collect(Collectors.toList());

			for (Person person : peopleWithThisCprAndDomain) {

				if (person.getSchoolRoles().isEmpty() && stilPeopleWithThisCpr.isEmpty()) {
					continue;
				}
				else if (person.getSchoolRoles().isEmpty() && !stilPeopleWithThisCpr.isEmpty()) {

					// all schoolRoles are new roles
					for (StilPerson stilPerson : stilPeopleWithThisCpr) {
						SchoolRole newRole = createSchoolRole(classMap, person, stilPerson);
						person.getSchoolRoles().add(newRole);
					}

					personService.save(person);
				}
				else {
					boolean changes = false;

					for (StilPerson stilPerson : stilPeopleWithThisCpr) {
						SchoolRole match = person.getSchoolRoles().stream()
								.filter(s -> Objects.equals(s.getInstitutionId(), stilPerson.getInstitutionNumber()) && Objects.equals(s.getRole(), stilPerson.getRole()))
								.findAny()
								.orElse(null);

						if (match == null) {
							SchoolRole newRole = createSchoolRole(classMap, person, stilPerson);
							person.getSchoolRoles().add(newRole);

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
										changes = true;
										match.getSchoolClasses().add(new SchoolRoleSchoolClassMapping(match, schoolClass));
									}
									else {
										log.warn("Unable to find a class with identifier: " + groupId + " / " + stilPerson.getInstitutionNumber());
									}
								}
							}

							// remove groups/classes that are not assigned to this role in STIL anymore
							Iterator<SchoolRoleSchoolClassMapping> iterator = match.getSchoolClasses().iterator();
							while (iterator.hasNext()) {
								SchoolRoleSchoolClassMapping mapping = iterator.next();
								SchoolClass schoolClass = mapping.getSchoolClass();
								String classMatch = stilPerson.getGroups().stream().filter(g -> g.equals(schoolClass.getClassIdentifier())).findAny().orElse(null);

								if (classMatch == null) {
									changes = true;
									iterator.remove();
									break;
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
							changes = true;
							iterator.remove();
							break;
						}
					}

					if (changes) {
						personService.save(person);
					}
				}
			}
		}
	}

	private void deleteNonExistingClasses(StilData stilData, List<SchoolClass> classes) {
		List<String> groupIds = stilData.getStudentGroups().stream().map(s -> s.getId()).collect(Collectors.toList());
		List<SchoolClass> toBeDeleted = classes.stream().filter(c -> !groupIds.contains(c.getClassIdentifier())).collect(Collectors.toList());

		schoolClassService.deleteAll(toBeDeleted);
	}

	private Map<String, SchoolClass> createAndUpdateClasses(StilData stilData, Domain domain, List<SchoolClass> existingClasses) {
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
