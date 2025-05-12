package dk.digitalidentity.rest.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.CachedMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.datatables.KodeviserDatatableDao;
import dk.digitalidentity.datatables.model.KodeviserView;
import dk.digitalidentity.security.RequireKodeviserAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.MFAManagementService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireKodeviserAdministrator
@RestController
public class KodeviserRestController {
	
	@Autowired
	private KodeviserDatatableDao kodeviserDatatableDao;
	
	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private MFAManagementService mfaManagementService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PersonService personService;

	@Autowired
	private CachedMfaClientService cachedMfaClientService;

	@Autowired
	private SettingService settingsService;

	@PostMapping("/rest/admin/kodeviser")
	public DataTablesOutput<KodeviserView> kodeviserDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<KodeviserView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		if (input != null && input.getColumns() != null && input.getColumn("locked") != null && input.getColumn("locked").getSearch() != null && input.getColumn("locked").getSearch().getValue() != null && !input.getColumn("locked").getSearch().getValue().equals("")) {
			String searchTerm = input.getColumn("locked").getSearch().getValue();
			input.getColumn("locked").getSearch().setValue("");
			
			if (searchTerm.toLowerCase().startsWith("s")) {
				return kodeviserDatatableDao.findAll(input, null, getByLocked(true));
			}
			else if (searchTerm.toLowerCase().startsWith("a")) {
				return kodeviserDatatableDao.findAll(input, null, getByLocked(false));
			}
		}
		
		return kodeviserDatatableDao.findAll(input);
	}

	private Specification<KodeviserView> getByLocked(boolean locked) {
		Specification<KodeviserView> specification = (root, query, criteriaBuilder) -> {
			return criteriaBuilder.equal(root.get("locked"), locked);
		};

		return specification;
	}

	@GetMapping("/rest/admin/kodeviser/deregister")
	@ResponseBody
	public ResponseEntity<?> deregisterDevice(@RequestParam("serial") String serial) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.warn("No admin logged in while deregistering " + serial);
			return ResponseEntity.badRequest().build();
		}

		boolean success = mfaManagementService.deregisterHardwareToken(serial);
		if (!success) {
			log.warn("Unable to deregister " + serial);
			return ResponseEntity.badRequest().build();
		}
		
		cachedMfaClientService.deleteBySerialnumber(serial);
		
		auditLogger.resetHardwareToken(serial, admin);
		
		return ResponseEntity.ok().build();
	}

	record SetRemoveDeviceSettingForm(Boolean removeDeviceSetting) {}
	
	@PostMapping("/rest/admin/kodeviser/removeDeviceSetting")
	public ResponseEntity<?> setRemoveDeviceSetting(@RequestBody SetRemoveDeviceSettingForm form) {
		if (form == null || form.removeDeviceSetting == null) {
			return ResponseEntity.badRequest().build();
		}

		settingsService.setBoolean(SettingKey.REMOVE_DEVICE_WHEN_PERSON_LOCKED, form.removeDeviceSetting.booleanValue());

		return ResponseEntity.ok().build();
	}
}
