package dk.digitalidentity.dev;

import java.time.LocalDateTime;
import java.util.List;

import javax.annotation.PostConstruct;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.service.DomainService;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;

@Controller
@Component
public class DevBootstrapper {

	@Autowired
	private PersonDao personDao;
	
	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private DomainService domainService;

	@Autowired
	private Flyway flyway;
	
	@PostConstruct
	public void init() {
		if (configuration.getDev().isEnabled()) {
			if (personDao.findAll().size() == 0) {
				Domain domain = domainService.getByName("digital-identity.dk", true);

				Person person = new Person();
				person.setUuid("54dfff62-b5ff-49d8-a1bd-e1e256043f5b");
				person.setAdmin(true);
				person.setCpr("2105791197");
				person.setEmail("bsg@digital-identity.dk");
				person.setName("Brian Storm Graversen");
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisAllowed(true);
				person.setSamaccountName("bsg");
				person.setDomain(domain);

				person = personDao.save(person);
				
				// add another Person
				person = new Person();
				person.setUuid("46889ca3-e686-4e77-b548-290343f178d0");
				person.setAdmin(true);
				person.setCpr("0701913477");
				person.setEmail("psu@digital-identity.dk");
				person.setName("Piotr Suski");
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisAllowed(true);
	
				person.setDomain(domain);
				person.setSamaccountName("psu");
	
				person = personDao.save(person);
				
				// add another Person
				
				person = new Person();
				person.setUuid("69afa825-2127-46aa-8eb6-3693b1092d1a");
				person.setAdmin(true);
				person.setCpr("1809960621");
				person.setName("Malthe Plenge Overgaard");
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisAllowed(true);
				person.setSamaccountName("mpo");
				person.setDomain(domain);

				person = personDao.save(person);

				person = new Person();
				person.setUuid("fb35b7a0-0cd1-475c-9c50-071c3d21a8fd");
				person.setAdmin(true);
				person.setCpr("0310990868");
				person.setName("Amalie Flensburg Bojsen");
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisAllowed(true);
				person.setSamaccountName("abo");
				person.setDomain(domain);

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
			}
		}
	}

	// TODO This is part of the nonsecured pages, should probably have "test" apiKey on endpoint
	@GetMapping("/bootstrap/db/clean")
	public ResponseEntity<?> cleanDB() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		flyway.clean();
		flyway.migrate();
		
		return new ResponseEntity<>(HttpStatus.OK);
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
			person.setNsisPassword(encoder.encode("Test123456"));
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
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setNsisLevel(NSISLevel.NONE);
			person.setAdPassword(encoder.encode("Test123456"));
			person.setAdPasswordTimestamp(LocalDateTime.now());
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
