package dk.digitalidentity.rest.admin;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.datatables.AuditLogDatatableDao;
import dk.digitalidentity.datatables.PersonDatatableDao;
import dk.digitalidentity.datatables.model.AdminPersonView;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.rest.admin.dto.PersonDataDTO;
import dk.digitalidentity.rest.admin.dto.ToggleAdminDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.RequireCoredataEditor;
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
	private OS2faktorConfiguration configuration;

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

		return auditLogDatatableDao.findAll(input, null, getAdditionalSpecification(person.getCpr()));
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

		return auditLogDatatableDao.findAll(input);
	}

	@PostMapping("/rest/admin/persons")
	public DataTablesOutput<AdminPersonView> addAdminDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AdminPersonView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		return personDatatableDao.findAll(input);
	}

	@PostMapping("/rest/admin/lock/{id}")
	@ResponseBody
	public ResponseEntity<?> adminLockAccount(@PathVariable("id") long id, @RequestParam("lock") boolean lock) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Person person = personService.getById(id);
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		person.setLockedAdmin(lock);
		personService.save(person);

		if (lock) {
			auditLogger.deactivateByAdmin(person, admin);
		}
		else {
			auditLogger.reactivateByAdmin(person, admin);
		}

		return new ResponseEntity<>(HttpStatus.OK);
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
				person.setSupporter(body.isState());
				auditLogger.toggleRoleByAdmin(person, admin, Constants.ROLE_SUPPORTER, body.isState());
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
					}
				} catch (InterruptedException | ExecutionException | TimeoutException ex) {
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

		// Validate existing AD Account
		personDTO.setDomain(configuration.getCoreData().getDomain());
		if (!StringUtils.isEmpty(personDTO.getSamAccountName())) {
			List<Person> matches = personService.getBySamaccountNameAndDomain(personDTO.getSamAccountName(), personDTO.getDomain());
			if (matches != null && !matches.isEmpty()) {
				for (Person match : matches) {
					if (!PersonDataDTO.compare(match, personDTO)) {
						return new ResponseEntity<>("Det valgte AD-Bruger er allerede tilknyttet en anden person inden for samme domæne", HttpStatus.CONFLICT);
					}
				}
			}
		}

		// Determine if it's a create or update scenario
		boolean newUser = false;
		Person person = personService.getById(personDTO.getPersonId());
		if (person == null) {
			newUser = true;

			person = new Person();
			person.setNsisLevel(NSISLevel.LOW);
			person.setCpr(personDTO.getCpr());
		}

		person.setUuid(personDTO.getUuid());
		person.setName(StringUtils.isEmpty(personDTO.getName()) ? null : personDTO.getName());
		person.setSamaccountName(StringUtils.isEmpty(personDTO.getSamAccountName()) ? null : personDTO.getSamAccountName());
		person.setEmail(StringUtils.isEmpty(personDTO.getEmail()) ? null : personDTO.getEmail());
		person.setDomain(configuration.getCoreData().getDomain());
		person.setAttributes(personDTO.getAttributes());
		person.setLockedDataset(false);
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
			if (!configuration.getCoreData().getDomain().equals(person.getDomain())) {
				return new ResponseEntity<>(HttpStatus.FORBIDDEN);
			}
		}

		auditLogger.deletedUser(person, admin);
		personService.delete(person);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
