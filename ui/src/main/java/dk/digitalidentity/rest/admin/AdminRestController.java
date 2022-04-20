package dk.digitalidentity.rest.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.ListJoin;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
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
import dk.digitalidentity.common.dao.model.CmsMessage;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.KombitSubsystem;
import dk.digitalidentity.common.dao.model.Link;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.RadiusClient;
import dk.digitalidentity.common.dao.model.RadiusClientCondition;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.Supporter;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RadiusClientConditionType;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.CmsMessageService;
import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.KombitSubSystemService;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.RadiusClientService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.dto.CprLookupDTO;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.datatables.AuditLogDatatableDao;
import dk.digitalidentity.datatables.PersonDatatableDao;
import dk.digitalidentity.datatables.model.AdminPersonView;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.RadiusClientDTO;
import dk.digitalidentity.mvc.admin.dto.SessionConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ConditionDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ServiceProviderDTO;
import dk.digitalidentity.rest.admin.dto.AuditLogViewDTO;
import dk.digitalidentity.rest.admin.dto.GroupDTO;
import dk.digitalidentity.rest.admin.dto.LinkDTO;
import dk.digitalidentity.rest.admin.dto.NameDTO;
import dk.digitalidentity.rest.admin.dto.PersonDataDTO;
import dk.digitalidentity.rest.admin.dto.ToggleAdminDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.RequireAdministratorOrUserAdministrator;
import dk.digitalidentity.security.RequireAnyAdminRole;
import dk.digitalidentity.security.RequireCoredataEditor;
import dk.digitalidentity.security.RequireRegistrant;
import dk.digitalidentity.security.RequireServiceProviderAdmin;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.LinkService;
import dk.digitalidentity.service.MetadataService;
import lombok.extern.slf4j.Slf4j;

@RequireAnyAdminRole
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

	@Autowired
	private MetadataService metadataService;
	
	@Autowired
	private KombitSubSystemService kombitSubsystemService;
	
	@Autowired
	private CmsMessageService cmsMessageService;
	
	@Autowired
	private RadiusClientService radiusClientService;

	@Autowired
	private GroupService groupService;
	
	@PersistenceContext
	private EntityManager entityManager;

	@RequireSupporter
	@PostMapping("/rest/admin/eventlog/{id}")
	public DataTablesOutput<AuditLogViewDTO> selfserviceEventLogsDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, @PathVariable("id") long id, Locale locale) {
		Person person = personService.getById(id);
		if (person == null) {
			return new DataTablesOutput<>();
		}

		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogViewDTO> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}
		
		Specification<AuditLogView> auditLogSpec = null;
		if (!securityUtil.isAdmin()) {
			Person loggedInPerson = personService.getById(securityUtil.getPersonId());

			// If we are filtering on domains (supporter role) we should show subdomains too.
			ArrayList<Domain> domains = new ArrayList<>();
			Domain domain = (loggedInPerson.isSupporter()) ? loggedInPerson.getSupporter().getDomain() : loggedInPerson.getTopLevelDomain();

			domains.add(domain);
			if (domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
				domains.addAll(domain.getChildDomains());
			}

			List<String> domainNames = domains.stream().map(Domain::getName).collect(Collectors.toList());

			auditLogSpec = getAuditLogByDomain(domainNames);
		}
		
		return convertAuditLogDataTablesModelToDTO(auditLogDatatableDao.findAll(input, auditLogSpec, getAdditionalSpecification(person.getCpr())), locale);
	}
		
	private Specification<AuditLogView> getAdditionalSpecification(String value) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("cpr"), value);
	}

	@RequireSupporter
	@PostMapping("/rest/admin/eventlog")
	public DataTablesOutput<AuditLogViewDTO> adminEventLogsDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, Locale locale) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogViewDTO> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}
		
		// column 3 is LogAction - lot of null checks and a check if we are searching on logAction
		if (input != null && input.getColumns() != null && input.getColumns().get(3) != null && input.getColumns().get(3).getSearch() != null && input.getColumns().get(3).getSearch().getValue() != null && !input.getColumns().get(3).getSearch().getValue().equals("")) {
			String searchTerm = input.getColumns().get(3).getSearch().getValue();

			// show either full output or filter by domain if not admin
			if (securityUtil.isAdmin()) {
				Specification<AuditLogView> auditLogByLogAction = getAuditLogByLogAction(searchTerm);
				DataTablesOutput<AuditLogView> x = auditLogDatatableDao.findAll(input, null, auditLogByLogAction);
				return convertAuditLogDataTablesModelToDTO(x, locale);
			}
			else {
				Person loggedInPerson = personService.getById(securityUtil.getPersonId());

				// if we are filtering on domains (supporter role) we should show subdomains too.
				ArrayList<Domain> domains = new ArrayList<>();
				Domain domain = (loggedInPerson.isSupporter()) ? loggedInPerson.getSupporter().getDomain() : loggedInPerson.getTopLevelDomain();

				domains.add(domain);

				if (domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
					domains.addAll(domain.getChildDomains());
				}

				List<String> domainNames = domains.stream().map(Domain::getName).collect(Collectors.toList());

				return convertAuditLogDataTablesModelToDTO(auditLogDatatableDao.findAll(input, getAuditLogByDomain(domainNames), getAuditLogByLogAction(searchTerm)), locale);
			}
		}

		// Show either full output or filter by domain if not admin
		if (securityUtil.isAdmin()) {
			return convertAuditLogDataTablesModelToDTO(auditLogDatatableDao.findAll(input), locale);
		}
		else {
			Person loggedInPerson = personService.getById(securityUtil.getPersonId());

			// If we are filtering on domains (supporter role) we should show subdomains too.
			ArrayList<Domain> domains = new ArrayList<>();
			Domain domain = (loggedInPerson.isSupporter()) ? loggedInPerson.getSupporter().getDomain() : loggedInPerson.getTopLevelDomain();

			domains.add(domain);
			if (domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
				domains.addAll(domain.getChildDomains());
			}

			List<String> domainNames = domains.stream().map(Domain::getName).collect(Collectors.toList());

			return convertAuditLogDataTablesModelToDTO(auditLogDatatableDao.findAll(input, getAuditLogByDomain(domainNames)), locale);
		}
	}
	
	private Specification<AuditLogView> getAuditLogByLogAction(String search) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("logAction"), LogAction.valueOf(search));
	}
	
	private Specification<AuditLogView> getAuditLogByDomain(List<String> domains) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get("personDomain")).value(domains);
	}
	
	private DataTablesOutput<AuditLogViewDTO> convertAuditLogDataTablesModelToDTO(DataTablesOutput<AuditLogView> output, Locale locale) {
		List<AuditLogViewDTO> dataWithMessages = output.getData().stream().map(auditlog -> new AuditLogViewDTO(auditlog, messageSource, locale)).collect(Collectors.toList());
		
		DataTablesOutput<AuditLogViewDTO> result = new DataTablesOutput<>();
		result.setData(dataWithMessages);
		result.setDraw(output.getDraw());
		result.setError(output.getError());
		result.setRecordsFiltered(output.getRecordsFiltered());
		result.setRecordsTotal(output.getRecordsTotal());

		return result;
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
			if (loggedInPerson.isSupporter()) {
				return personDatatableDao.findAll(input, getPersonByDomain(loggedInPerson.getSupporter().getDomain().getName()));
			}
			else {
				return personDatatableDao.findAll(input, getPersonByDomain(loggedInPerson.getTopLevelDomain().getName()));
			}
		}
	}

	private Specification<AdminPersonView> getPersonByDomain(String domain) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("domain"), domain);
	}

	@RequireSupporter
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
			auditLogger.deactivateByAdmin(person, admin, suspend);
		}
		else {
			auditLogger.reactivateByAdmin(person, admin);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireSupporter
	@GetMapping("/rest/admin/lock/check/{id}")
	public ResponseEntity<Boolean> checkHasNSISUser(@PathVariable("id") long id){
		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(false, HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(person.hasNSISUser(), HttpStatus.OK);
	}

	@RequireAdministratorOrUserAdministrator
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
		
		if (securityUtil.getPersonId() == person.getId()) {
			return new ResponseEntity<>("Man kan ikke tildele roller til sig selv", HttpStatus.BAD_REQUEST);
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
						return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
					}

					// Check if domain is a sub-domain since you can only be supporter of an entire domain not just subdomains
					if (domain.getParent() != null) {
						return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
					}

					person.setSupporter(new Supporter(domain));
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
			case Constants.ROLE_SERVICE_PROVIDER_ADMIN:
				person.setServiceProviderAdmin(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_SERVICE_PROVIDER_ADMIN, body.isState());
				personService.save(person);
				break;
			case Constants.ROLE_USER_ADMIN:
				person.setUserAdmin(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_USER_ADMIN, body.isState());
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
		personDataDTO.setDomain(domainService.getInternalDomain().getName());
		personDataDTO.setUuid(UUID.randomUUID().toString());
		personDataDTO.setCpr(cpr);
		personDataDTO.setNewPerson(true);

		if (configuration.getCoreData().isCprLookup()) {
			if (cpr.length() == 10) {
				try {
					Future<CprLookupDTO> cprFuture = cprService.getByCpr(cpr);
					CprLookupDTO dto = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;
					
					Person admin = personService.getById(securityUtil.getPersonId());
					auditLogger.cprLookupByAdmin(admin, cpr);

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
		Domain domain = domainService.getInternalDomain();

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
		person.setName(!StringUtils.hasLength(personDTO.getName()) ? null : personDTO.getName());
		person.setEmail(!StringUtils.hasLength(personDTO.getEmail()) ? null : personDTO.getEmail());
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
			if (!Objects.equals(domainService.getInternalDomain(), person.getDomain())) {
				return new ResponseEntity<>(HttpStatus.FORBIDDEN);
			}
		}

		personService.delete(person, admin);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/groups/edit")
	@ResponseBody
	public ResponseEntity<?> editGroup(@RequestBody GroupDTO groupDTO) {
		Group group = null;
		if (groupDTO.getId() == 0) {
			group = new Group();
			group.setDomain(domainService.getInternalDomain());
			group.setUuid(UUID.randomUUID().toString());
		}
		else {
			group = groupService.getById(groupDTO.getId());
		}

		// sanity check
		if (group == null || domainService.getInternalDomain().getId() != group.getDomain().getId()) {
			return ResponseEntity.badRequest().build();
		}

		if (!StringUtils.hasLength(groupDTO.getName())) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gruppen skal have et navn");
		}

		// update values
		group.setName(groupDTO.getName());
		group.setDescription(groupDTO.getDescription());
		group = groupService.save(group);

		return ResponseEntity.ok().body(group.getId());
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/groups/{id}/delete")
	public ResponseEntity<String> deleteGroup(@PathVariable("id") long id) {
		Group group = groupService.getById(id);
		if (group == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gruppen findes ikke");
		}

		if (domainService.getInternalDomain().getId() != group.getDomain().getId()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gruppen kommer fra en ekstern kilde (" + group.getDomain().getName() + ") og skal administeres der");
		}

		groupService.deleteById(id);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/groups/{id}/members/add")
	@ResponseBody
	public ResponseEntity<?> addGroupMember(@PathVariable("id") long groupId, @RequestBody long personId) {
		Group group = groupService.getById(groupId);
		if (group == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gruppen eksisterer ikke");
		}

		if (domainService.getInternalDomain().getId() != group.getDomain().getId()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gruppen kommer fra en ekstern kilde (" + group.getDomain().getName() + ") og skal administeres der");
		}

		Person person = personService.getById(personId);
		if (person == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Person eksisterer ikke");
		}

		if (!GroupService.memberOfGroup(person, Collections.singletonList(group))) {
			group.getMemberMapping().add(new PersonGroupMapping(person, group));
			group = groupService.save(group);
		}

		return ResponseEntity.ok().body(group.getId());
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/groups/{id}/members/remove")
	public ResponseEntity<?> removeGroupMember(@PathVariable("id") long groupId, @RequestBody long personId) {
		Group group = groupService.getById(groupId);
		if (group == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gruppen eksisterer ikke");
		}

		if (domainService.getInternalDomain().getId() != group.getDomain().getId()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gruppen kommer fra en ekstern kilde (" + group.getDomain().getName() + ") og skal administeres der");
		}

		Person person = personService.getById(personId);
		if (person == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Person eksisterer ikke");
		}

		if (GroupService.memberOfGroup(person, Collections.singletonList(group))) {
			group.getMemberMapping().removeIf(pgm -> pgm.getPerson().getId() == person.getId());
			group = groupService.save(group);
		}

		return ResponseEntity.ok().build();
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/settings/link/new")
	@ResponseBody
	public ResponseEntity<String> newLink(@RequestBody LinkDTO linkDTO) {
		Domain domain = domainService.getById(linkDTO.getDomainId());
		if (domain == null || !StringUtils.hasLength(linkDTO.getText()) || !StringUtils.hasLength(linkDTO.getLink())) {
			return new ResponseEntity<>("Alle felter skal være udfyldt", HttpStatus.BAD_REQUEST);
		}
		
		Link link = new Link();
		link.setLink(linkDTO.getLink());
		link.setLinkText(linkDTO.getText());
		link.setDomain(domain);

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
		PasswordConfigurationForm form = new PasswordConfigurationForm(settings);
		
		// only show these for parent domains
		form.setShowAdSettings(domain.getParent() == null);
		
		return ResponseEntity.ok(form);
	}

	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/tjenesteudbydere/edit")
	@ResponseBody
	public ResponseEntity<?> editServiceProvider(@RequestBody ServiceProviderDTO serviceProviderDTO) {
		try {
			SqlServiceProviderConfiguration config = metadataService.saveConfiguration(serviceProviderDTO);

			return ResponseEntity.ok(config.getId());
		}
		catch (Exception ex) {
			log.warn("Failed to save serviceprovider", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/tjenesteudbydere/{id}/reload")
	@ResponseBody
	public ResponseEntity<?> reloadMetadata(@PathVariable("id") long serviceProviderId) {
		try {
			boolean result = metadataService.setManualReload(serviceProviderId);

			if (result) {
				return ResponseEntity.ok().build();
			}
			else {
				return ResponseEntity.badRequest().build();
			}
		}
		catch (Exception ex) {
			log.warn("Failed to manual reload serviceprovider", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
	
	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/radiusklienter/{id}/edit")
	@ResponseBody
	public ResponseEntity<?> editRadiusClient(@RequestBody RadiusClientDTO radiusClientDTO) {
		if (radiusClientDTO.getName().isBlank() || radiusClientDTO.getIpAddress().isBlank()) {
			return new ResponseEntity<>("Alle felter skal have en værdi", HttpStatus.BAD_REQUEST);
		}
		
		if (!validIp(radiusClientDTO.getIpAddress())) {
			return new ResponseEntity<>("IP-adressen er ikke valid (/16, /24 eller /32 segmenter er tilladte)", HttpStatus.BAD_REQUEST);
		}

		RadiusClient radiusClient = null;

		if (radiusClientDTO.getId() == 0) {
			radiusClient = new RadiusClient();
			String password = UUID.randomUUID().toString();
			radiusClient.setPassword(password.replace("-", ""));
			radiusClient.setConditions(new HashSet<>());
		}
		else {
			radiusClient = radiusClientService.getById(radiusClientDTO.getId());
			if (radiusClient == null) {
				return ResponseEntity.notFound().build();
			}
		}
		
		radiusClient.setName(radiusClientDTO.getName());
		radiusClient.setIpAddress(radiusClientDTO.getIpAddress());

		// Handle conditions
		Set<RadiusClientCondition> conditions = radiusClient.getConditions();
		Set<RadiusClientCondition> newConditions = new HashSet<>();

		Set<RadiusClientCondition> domainConditions = conditions.stream().filter(condition -> RadiusClientConditionType.DOMAIN.equals(condition.getType())).collect(Collectors.toSet());
		List<ConditionDTO> conditionsDomains = radiusClientDTO.getConditionsDomains();
		for (ConditionDTO conditionsDomain : conditionsDomains) {
			Optional<RadiusClientCondition> condition = domainConditions.stream().filter(domainCondition -> domainCondition.getDomain().getId() == conditionsDomain.getId()).findAny();
			if (condition.isEmpty()) {
				// Check if domain is a sub-domain since radius clients works on parent domain level, not sub-domains
				Domain domain = domainService.getById(conditionsDomain.getId());
				if (domain != null && domain.getParent() == null) {
					newConditions.add(new RadiusClientCondition(radiusClient, RadiusClientConditionType.DOMAIN, null, domain));
				}
				else {
					return new ResponseEntity<>("Det valgte domæne findes ikke, eller er et subdomæne", HttpStatus.BAD_REQUEST);
				}
			}
			else {
				newConditions.add(condition.get());
			}
		}

		Set<RadiusClientCondition> groupConditions = conditions.stream().filter(condition -> RadiusClientConditionType.GROUP.equals(condition.getType())).collect(Collectors.toSet());
		List<ConditionDTO> conditionsGroups = radiusClientDTO.getConditionsGroups();
		for (ConditionDTO conditionsGroup : conditionsGroups) {
			Optional<RadiusClientCondition> condition = groupConditions.stream().filter(groupCondition -> groupCondition.getGroup().getId() == conditionsGroup.getId()).findAny();
			if (condition.isEmpty()) {
				Group group = groupService.getById(conditionsGroup.getId());
				if (group != null) {
					newConditions.add(new RadiusClientCondition(radiusClient, RadiusClientConditionType.GROUP, group, null));
				} else {
					return new ResponseEntity<>("Den valgte gruppe findes ikke", HttpStatus.BAD_REQUEST);
				}
			} else {
				newConditions.add(condition.get());
			}
		}

		Optional<RadiusClientCondition> withAttributeConditionOpt = conditions.stream().filter(condition -> RadiusClientConditionType.WITH_ATTRIBUTE.equals(condition.getType())).findAny();
		ConditionDTO conditionWithAttribute = radiusClientDTO.getConditionWithAttribute();
		if (conditionWithAttribute != null) {
			newConditions.add(withAttributeConditionOpt.isPresent() ? withAttributeConditionOpt.get() : new RadiusClientCondition(radiusClient, RadiusClientConditionType.WITH_ATTRIBUTE, null, null));
		}


		Set<RadiusClientCondition> allConditions = radiusClient.getConditions();
		allConditions.clear();
		allConditions.addAll(newConditions);
		radiusClient.setConditions(allConditions);

		radiusClient = radiusClientService.save(radiusClient);

		return ResponseEntity.ok(radiusClient.getId());
	}
	
	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/radiusklienter/{id}/delete")
	@ResponseBody
	public ResponseEntity<?> deleteRadiusClient(@PathVariable long id) {
		RadiusClient radiusClient = radiusClientService.getById(id);
		if (radiusClient == null) {
			return ResponseEntity.notFound().build();
		}
		
		radiusClientService.delete(radiusClient);
		
		return ResponseEntity.ok().build();
	}
	
	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/tjenesteudbydere/kombit/subsystem/{id}/mfa/{enabled}")
	@ResponseBody
	public ResponseEntity<?> editKombitSubSystemMfa(@PathVariable("id") long id, @PathVariable("enabled") boolean enabled) {
		KombitSubsystem subsystem = kombitSubsystemService.findById(id);
		if (subsystem == null) {
			log.warn("No subsystem with id " + id);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		if (!Objects.equals(enabled, subsystem.isAlwaysRequireMfa())) {
			subsystem.setAlwaysRequireMfa(enabled);
			kombitSubsystemService.save(subsystem);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/tjenesteudbydere/kombit/subsystem/{id}/name")
	@ResponseBody
	public ResponseEntity<?> editKombitSubSystemMfa(@PathVariable("id") long id, @RequestBody NameDTO name) {
		KombitSubsystem subsystem = kombitSubsystemService.findById(id);
		if (subsystem == null) {
			log.warn("No subsystem with id " + id);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		if (!Objects.equals(name.getName(), subsystem.getName())) {
			subsystem.setName(name.getName());
			kombitSubsystemService.save(subsystem);
		}

		return new ResponseEntity<>(HttpStatus.OK);
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
	
	@PostMapping("/rest/admin/savelogo")
	@ResponseBody
	public ResponseEntity<?> saveLogo(@RequestBody String base64) {
		// TODO: er det virkelig den bedste måde at teste det på?
		if (!base64.toLowerCase().contains("image/png")) {
			return new ResponseEntity<>("Logoet skal være .png", HttpStatus.BAD_REQUEST);
		}
		
		if (base64.length() > 65536) {
			return new ResponseEntity<>("Logoet er for stort. Max 64KB tillades.", HttpStatus.BAD_REQUEST);
		}

		CmsMessage logo = cmsMessageService.getByCmsKey("cms.logo");
		if (logo == null) {
			logo = new CmsMessage();
			logo.setCmsKey("cms.logo");
		}

		logo.setCmsValue(base64);
		logo.setLastUpdated(LocalDateTime.now());
		cmsMessageService.save(logo);
		
		return new ResponseEntity<HttpStatus>(HttpStatus.OK);
	}
	
	@RequireSupporter
	@PostMapping("/rest/admin/deletelocal/{deviceid}")
	public ResponseEntity<?> deleteLocalClient(@PathVariable(name = "deviceid") String deviceId) {
		LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(deviceId);
		if (localClient == null) {
			return ResponseEntity.notFound().build();
		}
		
		localRegisteredMfaClientService.delete(localClient);
		
		return ResponseEntity.ok().build();
	}
	
	@RequireAdministrator
	@PostMapping("/rest/admin/groups/{id}/members")
	public DataTablesOutput<AdminPersonView> getGroupMembers(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, @PathVariable long id) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AdminPersonView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}
		
		Group group = groupService.getById(id);
		if (group == null) {
			DataTablesOutput<AdminPersonView> error = new DataTablesOutput<>();
			error.setError("Could not find group");

			return error;
		}

		// Don't filter by domain because admin
		return personDatatableDao.findAll(input, getPersonByGroup(id));
	}
	
	private Specification<AdminPersonView> getPersonByGroup(long groupId) {
		//SELECT p.* FROM view_person_admin_identities p JOIN view_persons_groups pg ON pg.person_id = p.id WHERE pg.group_id = ?;
		Specification<AdminPersonView> specification = null;
		specification = (root, query, criteriaBuilder) -> {
			// get model of child table
			Metamodel metadataModel = entityManager.getMetamodel();
			EntityType<AdminPersonView> view_ = metadataModel.entity(AdminPersonView.class);

			// join from root, with field
			ListJoin<AdminPersonView,?> join = root.join(view_.getList("groups"));
			
			return criteriaBuilder.equal(join.get("groupId"), groupId);
	    };
		
		return specification;
	}
	
	private boolean validIp(String ip) {
		String[] split = ip.split("\\.");
		if (split.length != 4) {
			return false;
		}
		int split0 = Integer.parseInt(split[0]);
		int split1 = Integer.parseInt(split[1]);
		int split2 = Integer.parseInt(split[2]);
		if (split0 < 0 || split0 > 255 || split[0].length() > 3) {
			return false;
		}
		if (split1 < 0 || split1 > 255 || split[1].length() > 3) {
			return false;
		}
		if (split2 < 0 || split2 > 255 || split[2].length() > 3) {
			return false;
		}
		
		if (!split[3].contains("/")) {
			return false;
		} else {
			String[] lastSplit = split[3].split("/");
			if (lastSplit.length != 2) {
				return false;
			}
			int firstPartInt = Integer.parseInt(lastSplit[0]);
			if (firstPartInt < 0 || firstPartInt > 255) {
				return false;
			}
			
			int lastPartInt = Integer.parseInt(lastSplit[1]);
			if (lastPartInt != 32 && lastPartInt != 24 && lastPartInt != 16) {
				return false;
			}
		}
		
		return true;
	}
}
