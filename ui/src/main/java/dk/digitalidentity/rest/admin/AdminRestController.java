package dk.digitalidentity.rest.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Predicate;
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

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.model.AuditLogSearchCriteria;
import dk.digitalidentity.common.dao.model.BadPassword;
import dk.digitalidentity.common.dao.model.CmsMessage;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.KombitSubsystem;
import dk.digitalidentity.common.dao.model.Link;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PersonAttribute;
import dk.digitalidentity.common.dao.model.RadiusClient;
import dk.digitalidentity.common.dao.model.RadiusClientClaim;
import dk.digitalidentity.common.dao.model.RadiusClientCondition;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.Supporter;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RadiusClientConditionType;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.AdvancedRuleService;
import dk.digitalidentity.common.service.CmsMessageService;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.KombitSubSystemService;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonAttributeService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.RadiusClientService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.datatables.AuditLogDatatableDao;
import dk.digitalidentity.datatables.BadPasswordDatatableDao;
import dk.digitalidentity.datatables.PersonDatatableDao;
import dk.digitalidentity.datatables.model.AdminPersonView;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.RadiusClaimDTO;
import dk.digitalidentity.mvc.admin.dto.RadiusClientDTO;
import dk.digitalidentity.mvc.admin.dto.SessionConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ConditionDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ServiceProviderDTO;
import dk.digitalidentity.mvc.selfservice.NSISStatus;
import dk.digitalidentity.rest.admin.dto.AdvancedRuleDTO;
import dk.digitalidentity.rest.admin.dto.AuditLogViewDTO;
import dk.digitalidentity.rest.admin.dto.LinkDTO;
import dk.digitalidentity.rest.admin.dto.NameDTO;
import dk.digitalidentity.rest.admin.dto.ToggleAdminDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.RequireAdministratorOrUserAdministrator;
import dk.digitalidentity.security.RequireAnyAdminRole;
import dk.digitalidentity.security.RequireRegistrant;
import dk.digitalidentity.security.RequireServiceProviderAdmin;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.AuditLogSearchCriteriaService;
import dk.digitalidentity.service.BadPasswordService;
import dk.digitalidentity.service.EmailTemplateSenderService;
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
	private BadPasswordDatatableDao badPasswordDatatableDao;

	@Autowired
	private PersonDatatableDao personDatatableDao;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private DomainService domainService;

	@Autowired
	private SessionSettingService sessionSettingService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;
	
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

	@Autowired
	private PersonAttributeService personAttributeSetService;
	
	@Autowired
	private EmailTemplateSenderService emailTemplateSenderService;

	@Autowired
	private BadPasswordService badPasswordService;
	
	@PersistenceContext
	private EntityManager entityManager;
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private AdvancedRuleService advancedRuleService;

	@Autowired
	private AuditLogSearchCriteriaService auditLogSearchCriteriaService;

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

		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		Specification<AuditLogView> auditLogSpec = null;
		if (!securityUtil.isAdmin() && !(loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {

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
		
		return convertAuditLogDataTablesModelToDTO(auditLogDatatableDao.findAll(input, auditLogSpec, getAdditionalSpecification(person.getId())), locale);
	}
		
	private Specification<AuditLogView> getAdditionalSpecification(long value) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("personId"), value);
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

			Person loggedInPerson = personService.getById(securityUtil.getPersonId());

			// show either full output or filter by domain if not admin or supporter for all domains
			if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
				Specification<AuditLogView> auditLogByLogAction = getAuditLogByLogAction(searchTerm);
				DataTablesOutput<AuditLogView> x = auditLogDatatableDao.findAll(input, null, auditLogByLogAction);
				return convertAuditLogDataTablesModelToDTO(x, locale);
			}
			else {

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

		Person loggedInPerson = personService.getById(securityUtil.getPersonId());

		// Show either full output or filter by domain if not admin or supporter for all domains
		if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
			return convertAuditLogDataTablesModelToDTO(auditLogDatatableDao.findAll(input), locale);
		}
		else {

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

		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		
		// nsisStatus - lot of null checks and a check if we are searching on nsisStatus
		if (input != null && input.getColumns() != null && input.getColumn("nsisAllowed") != null && input.getColumn("nsisAllowed").getSearch() != null && input.getColumn("nsisAllowed").getSearch().getValue() != null && !input.getColumn("nsisAllowed").getSearch().getValue().equals("")) {
			String searchTerm = input.getColumn("nsisAllowed").getSearch().getValue();
			List<NSISStatus> searchList = Arrays.asList(searchTerm.split(",")).stream().map(s -> NSISStatus.valueOf(s)).collect(Collectors.toList());
			
			// Show either full output or filter by domain if not admin or supporter for all domains
			if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
				Specification<AdminPersonView> byNsisStatus = getByNsisStatusAndDomain(searchList, null);
				return personDatatableDao.findAll(input, null, byNsisStatus);
			}
			else {
				// If we are filtering on domains (supporter role) we should show subdomains too.
				ArrayList<Domain> domains = new ArrayList<>();
				Domain domain = (loggedInPerson.isSupporter()) ? loggedInPerson.getSupporter().getDomain() : loggedInPerson.getTopLevelDomain();

				domains.add(domain);
				if (domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
					domains.addAll(domain.getChildDomains());
				}

				List<String> domainNames = domains.stream().map(d -> d.getParent() != null ? (d.getParent().getName() + " - " + d.getName()) : d.getName()).collect(Collectors.toList());
				
				Specification<AdminPersonView> byNsisStatus = getByNsisStatusAndDomain(searchList, domainNames);
				
				return personDatatableDao.findAll(input, byNsisStatus);
			}
		}

		// Show either full output or filter by domain if not admin or supporter for all domains
		if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
			return personDatatableDao.findAll(input);
		}
		else {
			// If we are filtering on domains (supporter role) we should show subdomains too.
			ArrayList<Domain> domains = new ArrayList<>();
			Domain domain = (loggedInPerson.isSupporter()) ? loggedInPerson.getSupporter().getDomain() : loggedInPerson.getTopLevelDomain();

			domains.add(domain);
			if (domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
				domains.addAll(domain.getChildDomains());
			}

			List<String> domainNames = domains.stream().map(d -> d.getParent() != null ? (d.getParent().getName() + " - " + d.getName()) : d.getName()).collect(Collectors.toList());
			
			return personDatatableDao.findAll(input, getPersonByDomain(domainNames));
		}
	}
	
	private Specification<AdminPersonView> getByNsisStatusAndDomain (List<NSISStatus> statuses, List<String> domains) {
		Specification<AdminPersonView> specification = (root, query, criteriaBuilder) -> {
			
			Predicate finalPredicate = null;
			if (statuses.contains(NSISStatus.LOCKED_BY_EXPIRE)) {
				Predicate lockedPredicate = criteriaBuilder.equal(root.get("locked"), true);
				Predicate currentPredicate = criteriaBuilder.equal(root.get("lockedExpired"), true);
				finalPredicate = criteriaBuilder.and(lockedPredicate, currentPredicate);
			}
			
			if (statuses.contains(NSISStatus.LOCKED_BY_MUNICIPALITY)) {
				Predicate lockedPredicate = criteriaBuilder.equal(root.get("locked"), true);
				Predicate lockedAdminPredicate = criteriaBuilder.equal(root.get("lockedAdmin"), true);
				Predicate lockedDatasetPredicate = criteriaBuilder.equal(root.get("lockedDataset"), true);
				Predicate orPredicate = criteriaBuilder.or(lockedAdminPredicate, lockedDatasetPredicate);
				Predicate andPredicate = criteriaBuilder.and(lockedPredicate, orPredicate);
				
				if (finalPredicate == null) {
					finalPredicate = andPredicate;
				}
				else {
					finalPredicate = criteriaBuilder.or(finalPredicate, andPredicate);
				}
			}

			if (statuses.contains(NSISStatus.LOCKED_BY_SELF)) {
				Predicate lockedPredicate = criteriaBuilder.equal(root.get("locked"), true);
				Predicate lockedPersonPredicate = criteriaBuilder.equal(root.get("lockedPerson"), true);
				Predicate lockedPasswordPredicate = criteriaBuilder.equal(root.get("lockedPassword"), true);
				Predicate orPredicate = criteriaBuilder.or(lockedPersonPredicate, lockedPasswordPredicate);
				Predicate andPredicate = criteriaBuilder.and(lockedPredicate, orPredicate);
				
				if (finalPredicate == null) {
					finalPredicate = andPredicate;
				}
				else {
					finalPredicate = criteriaBuilder.or(finalPredicate, andPredicate);
				}
			}
			
			if (statuses.contains(NSISStatus.LOCKED_BY_STATUS)) {
				Predicate lockedPredicate = criteriaBuilder.equal(root.get("locked"), true);
				Predicate currentPredicate = criteriaBuilder.equal(root.get("lockedCivilState"), true);
				Predicate andPredicate = criteriaBuilder.and(lockedPredicate, currentPredicate);
				
				if (finalPredicate == null) {
					finalPredicate = andPredicate;
				}
				else {
					finalPredicate = criteriaBuilder.or(finalPredicate, andPredicate);
				}
			}
			
			if (statuses.contains(NSISStatus.NOT_ACTIVATED)) {
				Predicate notLockedPredicate = criteriaBuilder.equal(root.get("locked"), false);
				Predicate nsisAllowedPredicate = criteriaBuilder.equal(root.get("nsisAllowed"), true);
				Predicate nsisLevelPredicate = criteriaBuilder.equal(root.get("nsisLevel"), NSISLevel.NONE);
				Predicate andPredicate = criteriaBuilder.and(notLockedPredicate, nsisAllowedPredicate, nsisLevelPredicate);
				
				if (finalPredicate == null) {
					finalPredicate = andPredicate;
				}
				else {
					finalPredicate = criteriaBuilder.or(finalPredicate, andPredicate);
				}
			}
			
			if (statuses.contains(NSISStatus.ACTIVE)) {
				Predicate notLockedPredicate = criteriaBuilder.equal(root.get("locked"), false);
				Predicate nsisAllowedPredicate = criteriaBuilder.equal(root.get("nsisAllowed"), true);
				Predicate nsisLevelPredicate = criteriaBuilder.notEqual(root.get("nsisLevel"), NSISLevel.NONE);
				Predicate andPredicate = criteriaBuilder.and(notLockedPredicate, nsisAllowedPredicate, nsisLevelPredicate);

				if (finalPredicate == null) {
					finalPredicate = andPredicate;
				}
				else {
					finalPredicate = criteriaBuilder.or(finalPredicate, andPredicate);
				}
			}
			
			if (statuses.contains(NSISStatus.NOT_ISSUED)) {
				Predicate notLockedPredicate = criteriaBuilder.equal(root.get("locked"), false);
				Predicate nsisNotAllowedPredicate = criteriaBuilder.equal(root.get("nsisAllowed"), false);
				Predicate andPredicate = criteriaBuilder.and(notLockedPredicate, nsisNotAllowedPredicate);

				if (finalPredicate == null) {
					finalPredicate = andPredicate;
				}
				else {
					finalPredicate = criteriaBuilder.or(finalPredicate, andPredicate);
				}
			}
			
			if (domains != null && finalPredicate != null) {
				Predicate domainPredicate = criteriaBuilder.in(root.get("domain")).value(domains);
				
				finalPredicate = criteriaBuilder.and(finalPredicate, domainPredicate);
			}

			return finalPredicate;
	    };

	    return specification;
	}

	private Specification<AdminPersonView> getPersonByDomain(List<String> domains) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get("domain")).value(domains);
	}

	@RequireSupporter
	@PostMapping("/rest/admin/lock/{id}")
	@ResponseBody
	public ResponseEntity<?> adminLockAccount(@PathVariable("id") long id, @RequestParam("lock") boolean lock, @RequestParam(name = "reason", required = false) String reason) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		person.setLockedAdmin(lock);

		if (lock) {
			personService.suspend(person);
		}
		
		sendEmails(lock,person);

		personService.save(person);

		if (lock) {
			auditLogger.deactivateByAdmin(person, admin, reason);
		}
		else {
			auditLogger.reactivateByAdmin(person, admin);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private void sendEmails(boolean lock, Person person) {
		if (lock) {
			EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.PERSON_DEACTIVATED);
			for (EmailTemplateChild child : emailTemplate.getChildren()) {
				if (child.isEnabled()) {
					String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
					emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, false);
				}
			}
		}
		
		if (!lock && person.isNsisAllowed()) {
			EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.PERSON_DEACTIVATION_REPEALED);
			for (EmailTemplateChild child : emailTemplate.getChildren()) {
				if (child.isEnabled()) {
					String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
					emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, true);
				}
			}
		}
	}

	@RequireSupporter
	@GetMapping("/rest/admin/lock/check/{id}")
	public ResponseEntity<Boolean> checkHasNSISUser(@PathVariable("id") long id){
		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(false, HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(person.hasActivatedNSISUser(), HttpStatus.OK);
	}

	@RequireAdministratorOrUserAdministrator
	@PostMapping("/rest/admin/toggleAdmin/{id}")
	@ResponseBody
	public ResponseEntity<?> adminToggle(@PathVariable("id") long id, @RequestBody ToggleAdminDTO body) {
		if (os2faktorConfiguration.getCoreData().isRoleApiEnabled()) {
			log.warn("role api is enabled - rejecting access to role management");
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);			
		}

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
					Domain domain = null;
					if (body.getDomainId() != -1) {
						domain = domainService.getById(body.getDomainId());
						if (domain == null) {
							return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
						}

						// Check if domain is a sub-domain since you can only be supporter of an entire domain not just subdomains
						if (domain.getParent() != null) {
							return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
						}
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
				// simple extra check to ensure this role cannot be assigned (should not actually be shown in UI ;))
				if (!commonConfiguration.getCustomer().isEnableRegistrant()) {
					break;
				}
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
			case Constants.ROLE_KODEVISER_ADMIN:
				person.setKodeviserAdmin(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_KODEVISER_ADMIN, body.isState());
				personService.save(person);
				break;
			case Constants.ROLE_PASSWORD_RESET_ADMIN:
				person.setPasswordResetAdmin(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_PASSWORD_RESET_ADMIN, body.isState());
				personService.save(person);
				break;
			case Constants.ROLE_INSTITUTION_STUDENT_PASSWORD_ADMIN:
				person.setInstitutionStudentPasswordAdmin(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_INSTITUTION_STUDENT_PASSWORD_ADMIN, body.isState());
				personService.save(person);
				break;
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/settings/link/new")
	@ResponseBody
	public ResponseEntity<String> newLink(@RequestBody LinkDTO linkDTO) {
		Domain domain = domainService.getById(linkDTO.getDomainId());
		if (domain == null || !StringUtils.hasLength(linkDTO.getText()) || !StringUtils.hasLength(linkDTO.getLink())) {
			return new ResponseEntity<>("Link tekst, link adresse og domæne skal være udfyldt", HttpStatus.BAD_REQUEST);
		}
		
		Link link = linkService.getById(linkDTO.getId());
		if (link == null) {
			link = new Link();
		}

		if (linkDTO.getDescription() != null && linkDTO.getDescription().length() > 254) {
			linkDTO.setDescription(linkDTO.getDescription().substring(0, 250) + "...");
		}
		
		link.setLink(linkDTO.getLink());
		link.setLinkText(linkDTO.getText());
		link.setDomain(domain);
		link.setDescription(linkDTO.getDescription());

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
		
		// only show these for parent domains (and only those that are not standalone)
		form.setShowAdSettings(domain.getParent() == null && !domain.isStandalone());
		
		return ResponseEntity.ok(form);
	}
	
	@PostMapping("/rest/admin/konfiguration/badpassword")
	@ResponseBody
	@RequireAdministrator
	public DataTablesOutput<BadPassword> getBadPasswords(@RequestBody DataTablesInput input) {
		return badPasswordDatatableDao.findAll(input);
	}
	
	@RequireAdministrator
	@PostMapping("/rest/admin/konfiguration/badPassword/add")
	public ResponseEntity<?> addBadPassword(@RequestBody String badPassword) {
		if (badPasswordService.exists(badPassword)) {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}

		BadPassword bp = new BadPassword();
		bp.setPassword(badPassword);
		badPasswordService.save(bp);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAdministrator
	@PostMapping("/rest/admin/konfiguration/badPassword/remove/{id}")
	public ResponseEntity<?> removeBadPassword(@PathVariable("id") long id){
		badPasswordService.delete(id);

		return new ResponseEntity<>(HttpStatus.OK);
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
	@PostMapping("/admin/konfiguration/tjenesteudbydere/validateRule")
	@ResponseBody
	public ResponseEntity<?> editServiceProvider(@RequestBody AdvancedRuleDTO advancedRule) {
		try {
			// throws exception if rule is invalid
			advancedRuleService.evaluateRule(advancedRule.getRule(), securityUtil.getPerson());

			return ResponseEntity.ok("");
		}
		catch (Exception ex) {
			log.warn("Failed to validate rule (" + advancedRule.getRule() + "): " + ex.getMessage());
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/tjenesteudbydere/{id}/delete")
	@ResponseBody
	public ResponseEntity<?> deleteServiceProvider(@PathVariable("id") String serviceProviderId) {
		try {
			metadataService.deleteServiceProvider(Long.parseLong(serviceProviderId));

			return new ResponseEntity<>(HttpStatus.OK);
		}
		catch (Exception ex) {
			log.warn("Failed to delete serviceprovider", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	// synchronized as it is called from UI, and takes time to execute, and we don't want to expose a resource exhaustion point
	@RequireServiceProviderAdmin
	@PostMapping("/admin/konfiguration/tjenesteudbydere/{id}/reload")
	@ResponseBody
	public synchronized ResponseEntity<?> reloadMetadata(@PathVariable("id") String serviceProviderId) {
		try {
			boolean result = metadataService.setManualReload(serviceProviderId);

			// not critical, but let's also try to refresh the EntityID, in case it has changed
			try {
				metadataService.attemptToUpdateEntityId(Long.parseLong(serviceProviderId));
			}
			catch (Exception ex) {
				log.warn("Failed to refresh service provider entityId", ex);
			}
			
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
			radiusClient.setPassword(UUID.randomUUID().toString().replace("-", ""));
			radiusClient.setConditions(new HashSet<>());
			radiusClient.setClaims(new HashSet<>());
		}
		else {
			radiusClient = radiusClientService.getById(radiusClientDTO.getId());
			if (radiusClient == null) {
				return ResponseEntity.notFound().build();
			}
		}
		
		radiusClient.setName(radiusClientDTO.getName());
		radiusClient.setIpAddress(radiusClientDTO.getIpAddress());
		radiusClient.setNsisLevelRequired(radiusClientDTO.getNsisLevelRequired());

		// add new claims or update
		for (RadiusClaimDTO claimDto : radiusClientDTO.getClaims()) {
			boolean found = false;
			
			for (RadiusClientClaim existingClaim : radiusClient.getClaims()) {				
				if (Objects.equals(existingClaim.getPersonField(), claimDto.getPersonField())) {
					found = true;
					
					// potentially update attribute key
					if (existingClaim.getAttributeId() != claimDto.getAttributeId()) {
						existingClaim.setAttributeId(claimDto.getAttributeId());
					}

					break;
				}
			}
			
			if (!found) {
				RadiusClientClaim newClaim = new RadiusClientClaim();
				newClaim.setAttributeId(claimDto.getAttributeId());
				newClaim.setPersonField(claimDto.getPersonField());
				newClaim.setClient(radiusClient);

				radiusClient.getClaims().add(newClaim);
			}
		}
		
		// remove claims no longer present
		for (Iterator<RadiusClientClaim> iterator = radiusClient.getClaims().iterator(); iterator.hasNext();) {
			RadiusClientClaim existingClaim = iterator.next();
			boolean found = false;

			for (RadiusClaimDTO claimDto : radiusClientDTO.getClaims()) {

				if (Objects.equals(existingClaim.getPersonField(), claimDto.getPersonField())) {
					found = true;
					break;
				}
			}
			
			if (!found) {
				iterator.remove();
			}
		}
		
		// handle conditions
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
				}
				else {
					return new ResponseEntity<>("Den valgte gruppe findes ikke", HttpStatus.BAD_REQUEST);
				}
			}
			else {
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
	@PostMapping("/admin/konfiguration/tjenesteudbydere/kombit/subsystem/{id}/mfa/{forceMfa}")
	@ResponseBody
	public ResponseEntity<?> editKombitSubSystemMfa(@PathVariable("id") long id, @PathVariable("forceMfa") String forceMFA) {
		KombitSubsystem subsystem = kombitSubsystemService.findById(id);
		if (subsystem == null) {
			log.warn("No subsystem with id " + id);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		try {
			ForceMFARequired kombitMFARequired = ForceMFARequired.valueOf(forceMFA);

			if (!Objects.equals(kombitMFARequired, subsystem.getForceMfaRequired())) {
				subsystem.setForceMfaRequired(kombitMFARequired);
				kombitSubsystemService.save(subsystem);
			}

		}
		catch (IllegalArgumentException e) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
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

	@PostMapping("/rest/admin/logs/savedSearchCriteria/add")
	@ResponseBody
	public ResponseEntity<?> addSearchCriteria(@RequestBody AuditLogSearchCriteria body) {		
		auditLogSearchCriteriaService.save(body);

		return ResponseEntity.ok().build();
	}

	@PostMapping("/rest/admin/logs/savedSearchCriteria/remove/{id}")
	@ResponseBody
	public ResponseEntity<?> deleteSearchCriteria(@PathVariable("id") long id) {
		AuditLogSearchCriteria searchCriteria = auditLogSearchCriteriaService.getById(id);
		if (searchCriteria == null) {
			return ResponseEntity.badRequest().build();
		}

		auditLogSearchCriteriaService.delete(searchCriteria);

		return ResponseEntity.ok().build();
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

	@RequireServiceProviderAdmin
	@PostMapping("admin/konfiguration/person/attributes/{id}/setDisplayName")
	public ResponseEntity<?> setPersonAttributeDisplayName(@PathVariable long id, @RequestBody NameDTO name) {
		PersonAttribute personAttribute = personAttributeSetService.getById(id);
		if (personAttribute == null) {
			return ResponseEntity.badRequest().body("Kunne ikke finde attribut");
		}

		if (StringUtils.hasLength(name.getName())) {
			personAttribute.setDisplayName(name.getName());
		}
		else {
			personAttribute.setDisplayName(null);
		}
		
		personAttributeSetService.save(personAttribute);

		return ResponseEntity.ok().build();
	}

	private Specification<AdminPersonView> getPersonByGroup(long groupId) {
		// SELECT p.* FROM view_person_admin_identities p JOIN view_persons_groups pg ON pg.person_id = p.id WHERE pg.group_id = ?;
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
