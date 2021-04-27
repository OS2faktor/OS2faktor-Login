package dk.digitalidentity.rest.admin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Link;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.Supporter;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.LinkService;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.datatables.AuditLogDatatableDao;
import dk.digitalidentity.datatables.PersonDatatableDao;
import dk.digitalidentity.datatables.model.AdminPersonView;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.SessionConfigurationForm;
import dk.digitalidentity.rest.admin.dto.LinkDTO;
import dk.digitalidentity.rest.admin.dto.PersonDataDTO;
import dk.digitalidentity.rest.admin.dto.ToggleAdminDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.RequireCoredataEditor;
import dk.digitalidentity.security.RequireRegistrant;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.CprLookupDTO;
import dk.digitalidentity.service.CprService;
import lombok.extern.slf4j.Slf4j;

@RequireSupporter
@RestController
@Slf4j
public class AdminRestController {

	@Autowired
	private AuditLogDatatableDao auditLogDatatableDao;

	@Autowired
	private PersonDatatableDao personDatatableDao;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private CprService cprService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private SessionSettingService sessionSettingService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private LinkService linkService;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;

	@Autowired
	private MFAService mfaService;
	
	@Autowired
	private MessageSource messageSource;

	@PostMapping("/rest/admin/eventlog/{id}")
	public DataTablesOutput<AuditLogView> selfserviceEventLogsDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, @PathVariable("id") long id) {
		Person person = personService.getById(id);
		if (person == null) {
			return new DataTablesOutput<>();
		}
		
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		Specification<AuditLogView> auditLogSpec = null;
		if (!securityUtil.isAdmin()) {
			Person loggedInPerson = personService.getById(securityUtil.getPersonId());
			auditLogSpec = getAuditLogByDomain(loggedInPerson.getDomain().getName());
		}

		return auditLogDatatableDao.findAll(input, auditLogSpec, getAdditionalSpecification(person.getCpr()));
	}
	
	private Specification<AuditLogView> getAdditionalSpecification(String value) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("cpr"), value);
	}

	@PostMapping("/rest/admin/eventlog")
	public DataTablesOutput<AuditLogView> adminEventLogsDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		// Show either full output or filter by domain if not admin
		if (securityUtil.isAdmin()) {
			return auditLogDatatableDao.findAll(input);
		}
		else {
			Person loggedInPerson = personService.getById(securityUtil.getPersonId());
			return auditLogDatatableDao.findAll(input, getAuditLogByDomain(loggedInPerson.getDomain().getName()));
		}
	}

	private Specification<AuditLogView> getAuditLogByDomain(String domain) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("personDomain"), domain);
	}

	@PostMapping("/rest/admin/persons")
	public DataTablesOutput<AdminPersonView> addAdminDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AdminPersonView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		// custom hack for searching for rendered danish text
		if (input.getColumn("nsisLevel") != null && input.getColumn("nsisLevel").getSearch() != null) {
			String nsisSearchValue = input.getColumn("nsisLevel").getSearch().getValue();
			if (nsisSearchValue != null) {
				nsisSearchValue = nsisSearchValue.toLowerCase();
	
				if (nsisSearchValue.startsWith("b")) {
					input.getColumn("nsisLevel").getSearch().setValue("SUBSTANTIAL");
				}
				else if (nsisSearchValue.startsWith("l")) {
					input.getColumn("nsisLevel").getSearch().setValue("LOW");
				}
				else if (nsisSearchValue.startsWith("i")) {
					input.getColumn("nsisLevel").getSearch().setValue("NONE");
				}
			}
		}
		
		// custom hack for searching for rendered danish text
		if (input.getColumn("locked") != null && input.getColumn("locked").getSearch() != null) {
			String lockedSearchValue = input.getColumn("locked").getSearch().getValue();
			if (lockedSearchValue != null) {
				lockedSearchValue = lockedSearchValue.toLowerCase();
	
				if (lockedSearchValue.startsWith("s")) {
					input.getColumn("locked").getSearch().setValue("1");
				}
				else if (lockedSearchValue.startsWith("a")) {
					input.getColumn("locked").getSearch().setValue("0");
				}
			}
		}

		// Show either full output or filter by domain if not admin
		if (securityUtil.isAdmin()) {
			return personDatatableDao.findAll(input);
		}
		else {
			Person loggedInPerson = personService.getById(securityUtil.getPersonId());
			return personDatatableDao.findAll(input, getPersonByDomain(loggedInPerson.getDomain().getName()));
		}
	}

	private Specification<AdminPersonView> getPersonByDomain(String domain) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("domain"), domain);
	}

	@PostMapping("/rest/admin/lock/{id}")
	@ResponseBody
	public ResponseEntity<?> adminLockAccount(@PathVariable("id") long id, @RequestParam("lock") boolean lock, @RequestParam("suspend") boolean suspend) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		person.setLockedAdmin(lock);
		
		if (suspend) {
			personService.suspend(person);
		}
		
		personService.save(person);

		if (lock) {
			auditLogger.deactivateByAdmin(person, admin);
		}
		else {
			auditLogger.reactivateByAdmin(person, admin);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@GetMapping("/rest/admin/lock/check/{id}")
	public ResponseEntity<Boolean> checkHasNSISUser(@PathVariable("id") long id){
		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(false, HttpStatus.NOT_FOUND);
		}
		
		return new ResponseEntity<>(person.hasNSISUser(), HttpStatus.OK);
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/toggleAdmin/{id}")
	@ResponseBody
	public ResponseEntity<?> adminToggle(@PathVariable("id") long id, @RequestBody ToggleAdminDTO body) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		switch (body.getType()) {
			case Constants.ROLE_ADMIN:
				person.setAdmin(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_ADMIN, body.isState());
				personService.save(person);
				break;
			case Constants.ROLE_SUPPORTER:
				if (body.isState()) {
					Domain domain = domainService.getById(body.getDomainId());
					if (domain == null) {
						domain = domainService.getAll().get(0);
					}

					Supporter supporter = new Supporter(domain);
					person.setSupporter(supporter);
				}
				else {
					person.setSupporter(null);
				}

				personService.save(person);
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_SUPPORTER, body.isState());
				break;
			case Constants.ROLE_REGISTRANT:
				person.setRegistrant(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_REGISTRANT, body.isState());
				personService.save(person);
				break;
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireCoredataEditor
	@PostMapping("/rest/admin/coredata/edit/id")
	@ResponseBody
	public ResponseEntity<PersonDataDTO> getCoreDataById(@RequestBody long id) {
		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		PersonDataDTO personDataDTO = new PersonDataDTO(person);

		return new ResponseEntity<>(personDataDTO, HttpStatus.OK);
	}

	@RequireCoredataEditor
	@PostMapping("/rest/admin/coredata/edit/cpr")
	@ResponseBody
	public ResponseEntity<PersonDataDTO> getCoreDataByCpr(@RequestBody String cpr) {
		PersonDataDTO personDataDTO = new PersonDataDTO();
		personDataDTO.setPersonId(0);
		personDataDTO.setAttributes(new HashMap<>());
		personDataDTO.setDomain(configuration.getCoreData().getDomain());
		personDataDTO.setUuid(UUID.randomUUID().toString());
		personDataDTO.setCpr(cpr);
		personDataDTO.setNewPerson(true);

		if (configuration.getCoreData().isCprLookup()) {
			if (cpr.length() == 10) {
				try {
					Future<CprLookupDTO> cprFuture = cprService.getByCpr(cpr);
					CprLookupDTO dto = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;

					if (dto != null) {
						personDataDTO.setName(dto.getFirstname() + " " + dto.getLastname());
						personDataDTO.setNameProtected(dto.isAddressProtected());
					}
				}
				catch (InterruptedException | ExecutionException | TimeoutException ex) {
					log.warn("Could not fetch data from cpr within the timeout", ex);
				}
			}
		}

		return new ResponseEntity<>(personDataDTO, HttpStatus.OK);
	}

	@RequireCoredataEditor
	@PostMapping("/rest/admin/coredata/edit/save")
	@ResponseBody
	public ResponseEntity<String> saveCoreData(@RequestBody PersonDataDTO personDTO) {

		// Validate CPR-number
		if (personDTO.getCpr().length() != 10) {
			return new ResponseEntity<>("Personnummer er ikke gyldigt", HttpStatus.BAD_REQUEST);
		}
		
		if (personDTO.getName() == null || personDTO.getName().trim().length() < 2) {
			return new ResponseEntity<>("Udfyld navn", HttpStatus.BAD_REQUEST);
		}

		// validate UUID
		try {
			UUID.fromString(personDTO.getUuid());
		}
		catch (IllegalArgumentException ex) {
			return new ResponseEntity<>("Ugyldigt UUID", HttpStatus.BAD_REQUEST);
		}

		// Get CoreData domain (used for ui created people)
		Domain domain = domainService.getByName(configuration.getCoreData().getDomain(), true);

		// Determine if it's a create or update scenario
		boolean newUser = false;
		Person person = personService.getById(personDTO.getPersonId());
		if (person == null) {
			newUser = true;

			person = new Person();
			person.setNsisLevel(NSISLevel.LOW);
			person.setNsisAllowed(true);
			person.setCpr(personDTO.getCpr());
		}
		else {
			if (!Objects.equals(person.getDomain(), domain)) {
				return new ResponseEntity<>("Personen kommer fra et andet domæne", HttpStatus.BAD_REQUEST);
			}
		}

		person.setUuid(personDTO.getUuid());
		person.setName(StringUtils.isEmpty(personDTO.getName()) ? null : personDTO.getName());
		person.setEmail(StringUtils.isEmpty(personDTO.getEmail()) ? null : personDTO.getEmail());
		person.setDomain(domain);
		person.setAttributes(personDTO.getAttributes());
		person.setLockedDataset(false);
		person.setNameProtected(personDTO.isNameProtected());
		personService.save(person);

		Person admin = personService.getById(securityUtil.getPersonId());
		if (newUser) {
			auditLogger.createdUser(person, admin);
		}
		else {
			auditLogger.editedUser(person, admin);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireCoredataEditor
	@PostMapping("/rest/admin/coredata/delete")
	@ResponseBody
	public ResponseEntity<String> deletePerson(@RequestBody long id) {
		Person admin = personService.getById(securityUtil.getPersonId());
		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		else {
			if (!Objects.equals(configuration.getCoreData().getDomain(), person.getDomain().getName())) {
				return new ResponseEntity<>(HttpStatus.FORBIDDEN);
			}
		}

		personService.delete(person, admin);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAdministrator
	@PostMapping("/rest/admin/settings/link/new")
	@ResponseBody
	public ResponseEntity<String> newLink(@RequestBody LinkDTO linkDTO) {
		Link link = new Link();
		link.setLink(linkDTO.getLink());
		link.setLinkText(linkDTO.getText());
		
		linkService.save(link);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAdministrator
	@PostMapping("/rest/admin/settings/link/delete/{id}")
	public ResponseEntity<String> deleteLink(@PathVariable("id") long id) {
		linkService.deleteById(id);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@GetMapping("/rest/admin/settings/session/{domainId}")
	@ResponseBody
	@RequireAdministrator
	public ResponseEntity<SessionConfigurationForm> getSessionSettings(@PathVariable("domainId") long domainId) {
		Domain domain = domainService.getById(domainId);
		if (domain == null) {
			return ResponseEntity.badRequest().build();
		}

		SessionSetting settings = sessionSettingService.getSettings(domain);

		return ResponseEntity.ok(new SessionConfigurationForm(settings));
	}

	@GetMapping("/rest/admin/settings/password/{domainId}")
	@ResponseBody
	@RequireAdministrator
	public ResponseEntity<PasswordConfigurationForm> getPasswordSettings(@PathVariable("domainId") long domainId) {
		Domain domain = domainService.getById(domainId);
		if (domain == null) {
			return ResponseEntity.badRequest().build();
		}

		PasswordSetting settings = passwordSettingService.getSettings(domain);
		
		return ResponseEntity.ok(new PasswordConfigurationForm(settings));
	}
	
	@GetMapping("/rest/admin/registration/mfa/search")
	@ResponseBody
	@RequireRegistrant
	public ResponseEntity<?> searchMFADevice(@RequestParam("deviceId") String deviceId) {
		LocalRegisteredMfaClient existingLink = localRegisteredMfaClientService.getByDeviceId(deviceId);
		if (existingLink != null) {
			return ResponseEntity.badRequest().build();
		}

		MfaClient client = mfaService.getClient(deviceId);
		if (client == null) {
			return ResponseEntity.notFound().build();
		}
		
		client.setTypeMessage(messageSource.getMessage(client.getType().getMessage(), null, Locale.ENGLISH));

		return ResponseEntity.ok(client);
	}
}
