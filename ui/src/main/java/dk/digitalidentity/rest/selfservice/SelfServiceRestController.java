package dk.digitalidentity.rest.selfservice;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.dto.MfaAuthenticationResponseDTO;
import dk.digitalidentity.common.service.mfa.MFAManagementService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.datatables.AuditLogDatatableDao;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.mvc.admin.dto.ActivationDTO;
import dk.digitalidentity.rest.selfservice.dto.MfaRenameRequest;
import dk.digitalidentity.security.RequireEmployee;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequireEmployee
public class SelfServiceRestController {

	@Autowired
	private AuditLogDatatableDao auditLogDatatableDao;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PersonService personService;

	@Autowired
	private MFAService mfaService;

	@Autowired
	private MFAManagementService mfaManagementService;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;

	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;
	
	@PostMapping("/rest/selvbetjening/eventlog")
	public DataTablesOutput<AuditLogView> selfserviceEventLogsDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		return auditLogDatatableDao.findAll(input, null, getAdditionalSpecification(securityUtil.getPersonId()));
	}
	
	private Specification<AuditLogView> getAdditionalSpecification(long value) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("personId"), value);
	}

	@PostMapping("/rest/selvbetjening/rename")
	public ResponseEntity<String> renameClient(@RequestBody MfaRenameRequest request) {
		String name = request.getMfaNewName();
		if (name != null) {
			name = name.trim();
		}

		if (!StringUtils.hasLength(name)) {
			return ResponseEntity.badRequest().build();
		}
		
		if (name.length() > 255) {
			name = name.substring(0, 255);
		}

		Person person = personService.getById(securityUtil.getPersonId());
		
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr(), person.isRobot());
			MfaClient selectedClient = clients.stream().filter(c -> c.getDeviceId().equals(request.getMfaDeviceId())).findAny().orElse(null);

			if (selectedClient == null) {
				return ResponseEntity.notFound().build();
			}
			else {
				boolean result = mfaManagementService.renameMfaClient(selectedClient.getDeviceId(), name);
				
				if (!result) {
					return ResponseEntity.badRequest().build();
				}
			}
		}

		return ResponseEntity.ok().build();
	}
	
	@PostMapping("/rest/selvbetjening/delete/{deviceId}")
	public ResponseEntity<String> deleteClient(@PathVariable("deviceId") String deviceId) {
		Person person = personService.getById(securityUtil.getPersonId());
		
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr(), person.isRobot());
			MfaClient selectedClient = clients.stream().filter(c -> c.getDeviceId().equals(deviceId)).findAny().orElse(null);

			if (selectedClient == null) {
				return ResponseEntity.notFound().build();
			}
			else {
				if (selectedClient.isLocalClient()) {
					LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(selectedClient.getDeviceId());
					if (localClient == null) {
						return ResponseEntity.badRequest().build();
					}
					
					localRegisteredMfaClientService.delete(localClient);
					auditLogger.deletedMFADevice(person, selectedClient.getName(), deviceId, selectedClient.getType().toString());

					return ResponseEntity.ok().build();
				}
				else {
					boolean result = mfaManagementService.deleteMfaClient(selectedClient.getDeviceId());
					
					if (!result) {
						return ResponseEntity.badRequest().build();
					}
					else {
						auditLogger.deletedMFADevice(person, selectedClient.getName(), deviceId, selectedClient.getType().toString());

						return ResponseEntity.ok().build();
					}
				}
			}
		}

		return ResponseEntity.badRequest().build();
	}
	
	@PostMapping("/rest/selvbetjening/primary/{deviceId}")
	public ResponseEntity<String> setPrimaryClient(@PathVariable("deviceId") String deviceId, @RequestParam("setPrimary") boolean setPrimary) {
		Person person = personService.getById(securityUtil.getPersonId());
		
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr(), person.isRobot());
			MfaClient selectedClient = clients.stream().filter(c -> c.getDeviceId().equals(deviceId)).findAny().orElse(null);

			if (selectedClient == null) {
				return ResponseEntity.notFound().build();
			}
			else {
				if (selectedClient.isLocalClient()) {
					LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(selectedClient.getDeviceId());
					if (localClient == null) {
						return ResponseEntity.badRequest().build();
					}
					
					localRegisteredMfaClientService.setPrimaryClient(localClient, setPrimary, person.getCpr());

					return ResponseEntity.ok().build();
				}
				else {
					boolean result = mfaManagementService.setPrimaryMfaClient(selectedClient.getDeviceId(), setPrimary);
					
					if (!result) {
						return ResponseEntity.badRequest().build();
					}
					else {
						localRegisteredMfaClientService.removeAllPrimaryClient(person.getCpr());
						return ResponseEntity.ok().build();
					}
				}
			}
		}

		return ResponseEntity.badRequest().build();
	}

	@GetMapping("/rest/selvbetjening/findDevice/{deviceId}")
	public ResponseEntity<?> findDevice(@PathVariable("deviceId") String deviceId) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("No logged in person - this is unexpected!");

			return ResponseEntity.badRequest().build();			
		}

		deviceId = ensureCorrectlyFormattedDeviceId(deviceId);

		MfaClient matchingClient = mfaService.getClient(deviceId);
		if (matchingClient == null) {
			return ResponseEntity.notFound().build();
		}
		
		LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(deviceId);
		if (localClient != null) {
			log.warn("2-faktor enhed er allerede associeret til en brugerkonto");
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok().build();
	}

	@PostMapping("/rest/selvbetjening/confirmNewDevice/{deviceId}")
	public ResponseEntity<?> confirmNewDevice(@PathVariable("deviceId") String deviceId, HttpServletRequest request) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("No logged in person - this is unexpected!");

			return ResponseEntity.badRequest().build();
		}
		
		MfaClient matchingClient = mfaService.getClient(deviceId);
		if (matchingClient == null) {
			return ResponseEntity.notFound().build();
		}

		LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(deviceId);
		if (localClient != null) {
			log.warn("2-faktor enhed er allerede associeret til en brugerkonto");
			return ResponseEntity.notFound().build();
		}

		// start MFA authentication
		MfaAuthenticationResponseDTO mfaResponseDto = mfaService. authenticate(matchingClient.getDeviceId(), false);
		if (!mfaResponseDto.isSuccess()) {
			log.warn("Got an excpetion from response from mfaService.authenticate() on deviceID = " + matchingClient.getDeviceId() + " exception: " + mfaResponseDto.getFailureMessage());
			return ResponseEntity.status(500).build();
		}

		// store subscription key on session, so we can verify later
		request.getSession().setAttribute("subscriptionKey", mfaResponseDto.getMfaAuthenticationResponse().getSubscriptionKey());

		// show challenge page
		Map<String, Object> responseBody = new HashMap<>();
		responseBody.put("pollingKey", mfaResponseDto.getMfaAuthenticationResponse().getPollingKey());
		responseBody.put("challenge", mfaResponseDto.getMfaAuthenticationResponse().getChallenge());
		responseBody.put("wakeEvent", ClientType.CHROME.equals(matchingClient.getType()) || ClientType.EDGE.equals(matchingClient.getType()));

		return ResponseEntity.ok(responseBody);
	}

	@PostMapping("/rest/selvbetjening/challenge/{deviceId}/completed")
	public ResponseEntity<Boolean> mfaChallengeDone(@PathVariable("deviceId") String deviceId, HttpSession session) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("No logged in person - this is unexpected!");

			return ResponseEntity.badRequest().build();
		}

		MfaClient mfaClient = mfaService.getClient(deviceId);
		if (mfaClient == null) {
			log.warn("Could not find MFA device with deviceId: " + deviceId);
			return ResponseEntity.notFound().build();
		}

		LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(deviceId);
		if (localClient != null) {
			log.warn("2-faktor enhed er allerede associeret til en brugerkonto");
			return ResponseEntity.notFound().build();
		}

		String subscriptionKey = (String) session.getAttribute("subscriptionKey");

		if (subscriptionKey != null) {
			boolean authenticated = mfaService.isAuthenticated(subscriptionKey, person);

			if (authenticated) {
				NSISLevel aal = securityUtil.getAuthenticationAssuranceLevel();

				LocalRegisteredMfaClient client = new LocalRegisteredMfaClient();
				client.setDeviceId(mfaClient.getDeviceId());
				client.setName(mfaClient.getName());
				client.setType(mfaClient.getType());
				client.setNsisLevel(aal);
				client.setCpr(person.getCpr());
				client.setAssociatedUserTimestamp(new Date());

				localRegisteredMfaClientService.save(client);

				// add useful logging information
				ActivationDTO activationDTO = new ActivationDTO();
				activationDTO.setDeviceId(mfaClient.getDeviceId());
				activationDTO.setType(mfaClient.getType());
				activationDTO.setName(mfaClient.getName());
				activationDTO.setNsisLevel(aal);

				auditLogger.manualMfaAssociation(activationDTO.toIdentificationDetails(resourceBundle), person);
			}

			return ResponseEntity.ok(authenticated);
		}

		return ResponseEntity.badRequest().build();
	}
	
	@GetMapping("/rest/selvbetjening/unlockAccount")
	public ResponseEntity<?> unlockAccount() {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("No logged in person - this is unexpected!");

			return ResponseEntity.badRequest().build();
		}
		
		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.warn("Person has no samaccountName which means that there are no AD account to unlock.");
			return ResponseEntity.badRequest().build();
		}

		ADPasswordStatus adPasswordStatus = personService.unlockAccount(person, null);
		
		if (ADPasswordResponse.isCritical(adPasswordStatus)) {
			return ResponseEntity.badRequest().build();
		}

		return ResponseEntity.ok().build();
	}

	private static String ensureCorrectlyFormattedDeviceId(String deviceId) {
		if (deviceId == null) {
			return null;
		}

		// Remove any non digit characters
		String result = deviceId.replaceAll("\\D", "");
		if (result.length() != 12) {
			return deviceId;
		}

		// Reconstruct string
		String s1 = result.substring(0,3);
		String s2 = result.substring(3,6);
		String s3 = result.substring(6,9);
		String s4 = result.substring(9,12);
		return s1 + "-" + s2 + "-" + s3 + "-" + s4;
	}
}
