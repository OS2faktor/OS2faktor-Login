package dk.digitalidentity.dev;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.SchoolClassDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.SchoolClassType;
import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import dk.digitalidentity.common.dao.model.mapping.SchoolRoleSchoolClassMapping;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PasswordChangeQueueService;

@Controller
@Component
public class DevBootstrapper {
	private SecureRandom random = new SecureRandom();

	@Autowired
	private PersonDao personDao;
	
	@Autowired
	private SchoolClassDao schoolClassDao;
	
	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private DomainService domainService;

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		if (configuration.getDev().isEnabled()) {
			if (personDao.findAll().isEmpty()) {
				Domain domain = domainService.getByName("OS2faktor", true);

				// create a few classes
				
				SchoolClass class1A = new SchoolClass();
				class1A.setClassIdentifier("1A");
				class1A.setInstitutionId("12345678");
				class1A.setLevel("1");
				class1A.setName("1.A");
				class1A.setType(SchoolClassType.MAIN_GROUP);
				class1A = schoolClassDao.save(class1A);

				SchoolClass class7B = new SchoolClass();
				class7B.setClassIdentifier("7B");
				class7B.setInstitutionId("12345678");
				class7B.setLevel("7");
				class7B.setName("7.B");
				class7B.setType(SchoolClassType.MAIN_GROUP);
				class7B = schoolClassDao.save(class7B);

				Person person = new Person();
				person.setUuid("14deef62-b5ff-49d8-a1bd-e1e256043f5c");
				person.setAdmin(true);
				person.setCpr("0806500178");
				person.setEmail("ellie@digital-identity.dk");
				person.setName("Ellie Ellisen");
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisAllowed(true);
				person.setSamaccountName("ellie999");
				person.setDomain(domain);

				person = personDao.save(person);
				
				person = new Person();
				person.setUuid("54dfff62-b5ff-49d8-a1bd-e1e256043f5b");
				person.setAdmin(true);
				person.setCpr("2105791197");
				person.setEmail("bsg@digital-identity.dk");
				person.setName("Brian Storm Graversen");
				person.setNsisLevel(NSISLevel.SUBSTANTIAL);
				person.setNsisAllowed(true);
				person.setSamaccountName("bsg");
				person.setDomain(domain);
				person.setPassword("$2a$10$4z1bRdjRX8VMmJWi3taJeu3Nu6ig24IvU4.swD5Sh.V2UJKki8Hva");

				SchoolRole teacherRole = new SchoolRole();
				teacherRole.setInstitutionId("12345678");
				teacherRole.setInstitutionName("Folkeskolen");
				teacherRole.setPerson(person);
				teacherRole.setRole(SchoolRoleValue.TEACHER);
				teacherRole.setSchoolClasses(new ArrayList<>());
				
				SchoolRoleSchoolClassMapping mapping1 = new SchoolRoleSchoolClassMapping();
				mapping1.setSchoolClass(class1A);
				mapping1.setSchoolRole(teacherRole);
				teacherRole.getSchoolClasses().add(mapping1);

				SchoolRoleSchoolClassMapping mapping2 = new SchoolRoleSchoolClassMapping();
				mapping2.setSchoolClass(class7B);
				mapping2.setSchoolRole(teacherRole);
				teacherRole.getSchoolClasses().add(mapping2);

				person.setSchoolRoles(new ArrayList<>());
				person.getSchoolRoles().add(teacherRole);
				
				person = personDao.save(person);
				
				// add another Person
				person = new Person();
				person.setUuid("46889ca3-e686-4e77-b548-290343f178d0");
				person.setAdmin(true);
				person.setCpr("0701913477");
				person.setEmail("psu@digital-identity.dk");
				person.setName("Piotr Suski");
				person.setNsisLevel(NSISLevel.SUBSTANTIAL);
				person.setNsisAllowed(true);
				person.setDomain(domain);
				person.setSamaccountName("psu");
				person.setPassword("$2a$10$4z1bRdjRX8VMmJWi3taJeu3Nu6ig24IvU4.swD5Sh.V2UJKki8Hva");
	
				person = personDao.save(person);
				
				// add another Person
				
				person = new Person();
				person.setUuid("69afa825-2127-46aa-8eb6-3693b1092d1a");
				person.setAdmin(true);
				person.setCpr("1809960621");
				person.setName("Malthe Plenge Overgaard");
				person.setNsisLevel(NSISLevel.SUBSTANTIAL);
				person.setNsisAllowed(true);
				person.setSamaccountName("mpo");
				person.setDomain(domain);
				person.setPassword("$2a$10$4z1bRdjRX8VMmJWi3taJeu3Nu6ig24IvU4.swD5Sh.V2UJKki8Hva");

				person = personDao.save(person);

				person = new Person();
				person.setUuid("fb35b7a0-0cd1-475c-9c50-071c3d21a8fd");
				person.setAdmin(true);
				person.setCpr("0310990868");
				person.setName("Amalie Flensburg Bojsen");
				person.setNsisLevel(NSISLevel.SUBSTANTIAL);
				person.setNsisAllowed(true);
				person.setSamaccountName("abo");
				person.setDomain(domain);
				person.setPassword("$2a$10$4z1bRdjRX8VMmJWi3taJeu3Nu6ig24IvU4.swD5Sh.V2UJKki8Hva");

				person = personDao.save(person);

				person = new Person();
				person.setUuid("8e989f34-fabd-463c-9a63-5936acb2c657");
				person.setAdmin(true);
				person.setCpr("2105791197");
				person.setName("Ikke En Nsis Bruger");
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisAllowed(false);
				person.setSamaccountName("nnu");
				person.setDomain(domain);

				person = personDao.save(person);

				List<Person> persons = new ArrayList<>();
				for (int i = 0; i < 5000; i++) {
					person = new Person();
					person.setUuid(UUID.randomUUID().toString());
					person.setCpr(randomCpr());
					person.setName(randomName());
					person.setNsisLevel(NSISLevel.NONE);
					person.setNsisAllowed(random.nextBoolean());
					person.setSamaccountName(randomUserId());
					person.setDomain(domain);
					
					if (i % 100 == 0) {
						if (i % 200 == 0) {
							SchoolRole schoolRole = new SchoolRole();
							schoolRole.setInstitutionId("12345678");
							schoolRole.setInstitutionName("Folkeskolen");
							schoolRole.setPerson(person);
							schoolRole.setRole(SchoolRoleValue.STUDENT);
							schoolRole.setSchoolClasses(new ArrayList<>());
							
							SchoolRoleSchoolClassMapping mapping = new SchoolRoleSchoolClassMapping();
							mapping.setSchoolClass(class1A);
							mapping.setSchoolRole(schoolRole);
							schoolRole.getSchoolClasses().add(mapping);
							
							person.setSchoolRoles(new ArrayList<>());
							person.getSchoolRoles().add(schoolRole);
							
							try {
								String encrypted = passwordChangeQueueService.encryptPassword("Test1234-" + i);
								person.setStudentPassword(encrypted);
							}
							catch (Exception ignored) {
								;
							}
						}
						else {
							SchoolRole schoolRole = new SchoolRole();
							schoolRole.setInstitutionId("12345678");
							schoolRole.setInstitutionName("Folkeskolen");
							schoolRole.setPerson(person);
							schoolRole.setRole(SchoolRoleValue.STUDENT);
							schoolRole.setSchoolClasses(new ArrayList<>());
							
							SchoolRoleSchoolClassMapping mapping = new SchoolRoleSchoolClassMapping();
							mapping.setSchoolClass(class7B);
							mapping.setSchoolRole(schoolRole);
							schoolRole.getSchoolClasses().add(mapping);
							
							person.setSchoolRoles(new ArrayList<>());
							person.getSchoolRoles().add(schoolRole);
						}
					}
					
					persons.add(person);
				}
				
				personDao.saveAll(persons);
			}
		}
	}

	private String getInt(int min, int max) {
		int val = random.nextInt(max + 1 - min) + min;
		int width = (int) Math.ceil(Math.log10(max));
		
		String result = "" + val;
		while (result.length() < width) {
			result = "0" + result;
		}
		
		return result;
	}
	
	private String randomCpr() {
		return getInt(1, 28) + getInt(1, 12) + getInt(1,99) + getInt(0,9999);
	}
	
	private String randomName() {
		String result = "";
		switch (random.nextInt(4)) {
			case 0:
				result = "Jane ";
				break;
			case 1:
				result = "John ";
				break;
			case 2:
				result = "Jackson ";
				break;
			case 3:
				result = "Julie ";
				break;
		}
		
		switch (random.nextInt(4)) {
			case 0:
				result += "Petersen ";
				break;
			case 1:
				result += "Jensen ";
				break;
			case 2:
				result += "Hansen ";
				break;
			case 3:
				result += "Svendsen ";
				break;
		}

		return result + getInt(1, 9999);
	}
	
	private String randomUserId() {
		return "user" + getInt(1, 999999);
	}

	@GetMapping("/bootstrap/users/init")
	public ResponseEntity<?> initUsers() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		init();
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@GetMapping("/bootstrap/users/setPassword")
	public ResponseEntity<?> setPassword() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setNsisLevel(NSISLevel.SUBSTANTIAL);
			person.setPassword(encoder.encode("Test123456"));
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/bootstrap/users/setApprovedConditions")
	public ResponseEntity<?> setApprovedConditions() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setApprovedConditions(true);
			person.setApprovedConditionsTts(LocalDateTime.now());
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/bootstrap/users/setNSISAllowed")
	public ResponseEntity<?> setNSISAllowed() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setNsisAllowed(true);
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/bootstrap/users/setAdPassword")
	public ResponseEntity<?> setADPassword() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setNsisLevel(NSISLevel.NONE);
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
