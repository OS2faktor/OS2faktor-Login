package dk.digitalidentity.api;

import dk.digitalidentity.api.dto.CoreDataForceChangePassword;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.CoreData;
import dk.digitalidentity.api.dto.CoreDataDelete;
import dk.digitalidentity.api.dto.CoreDataDeltaJfr;
import dk.digitalidentity.api.dto.CoreDataEntryLight;
import dk.digitalidentity.api.dto.CoreDataFullJfr;
import dk.digitalidentity.api.dto.CoreDataGroupLoad;
import dk.digitalidentity.api.dto.CoreDataKombitAttributeEntry;
import dk.digitalidentity.api.dto.CoreDataKombitAttributesLoad;
import dk.digitalidentity.api.dto.CoreDataNemLoginAllowed;
import dk.digitalidentity.api.dto.CoreDataExtendedLookup;
import dk.digitalidentity.api.dto.CoreDataNemLoginStatus;
import dk.digitalidentity.api.dto.CoreDataNsisAllowed;
import dk.digitalidentity.api.dto.CoreDataStatus;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.service.CoreDataLogService;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.service.CoreDataService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CoreDataApi {
	public enum PersonRoles { ADMIN, TU_ADMIN, SUPPORTER, REGISTRANT, USER_ADMIN, KODEVISER_ADMIN, PASSWORD_RESET_ADMIN, STUDENT_PASSWORD_RESET_ADMIN }

	@Autowired
	private CoreDataService coreDataService;

	@Autowired
	private CoreDataLogService coreDataLogService;

	@Autowired
	private DomainService domainService;

	@PostMapping("/api/coredata/full")
	public ResponseEntity<?> fullLoad(@RequestBody CoreData coreData) {
		try {
			coreDataService.load(coreData, true);

			coreDataLogService.addLog("/api/coredata/full", coreData.getDomain(), coreData.getGlobalSubDomain());

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for fullLoad", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/api/coredata/delta")
	public ResponseEntity<?> deltaLoad(@RequestBody CoreData coreData) {
		try {
			coreDataService.load(coreData, false);

			coreDataLogService.addLog("/api/coredata/delta", coreData.getDomain(), coreData.getGlobalSubDomain());

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for deltaLoad", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	// TODO: members skal være samAccountNames, og så skal der opdateres kode i AD + AD Azure og sendes opdateringer ud (måske understøtte UUID'er i en kort overgangsperiode,
    //	         som en slags backup kode)
	@PostMapping("/api/coredata/groups/load/full")
	public ResponseEntity<?> loadGroupsFully(@RequestBody CoreDataGroupLoad groups) {
		try {
			coreDataService.loadGroupsFull(groups);

			coreDataLogService.addLog("/api/coredata/groups/load/full", groups.getDomain(), null);

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for Group load", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	// TODO: members skal være samAccountNames, og så skal der opdateres kode i AD + AD Azure og sendes opdateringer ud (måske understøtte UUID'er i en kort overgangsperiode,
    //	         som en slags backup kode)

	@PostMapping("/api/coredata/groups/load/delta")
	public ResponseEntity<?> loadGroupsDelta(@RequestBody CoreDataGroupLoad groups) {
		try {
			coreDataService.loadGroupsDelta(groups);

			coreDataLogService.addLog("/api/coredata/groups/load/delta", groups.getDomain(), null);

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for Group load", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	// TODO: members skal være samAccountNames, og så skal der opdateres kode i AD + AD Azure og sendes opdateringer ud (måske understøtte UUID'er i en kort overgangsperiode,
    //	         som en slags backup kode)
	@PostMapping("/api/coredata/kombitAttributes/load/full")
	public ResponseEntity<?> loadKombitAttributesFully(@RequestBody CoreDataKombitAttributesLoad kombitAttributes) {
		try {
			coreDataService.loadKombitAttributesFull(kombitAttributes);

			coreDataLogService.addLog("/api/coredata/kombitAttributes/load/full", kombitAttributes.getDomain(), null);

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for KombitAttributes load", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
	
	// TODO: members skal være samAccountNames, og så skal der opdateres kode i AD + AD Azure og sendes opdateringer ud (måske understøtte UUID'er i en kort overgangsperiode,
    //	         som en slags backup kode)
	@PostMapping("/api/coredata/nsisallowed/load")
	public ResponseEntity<?> loadNSISAllowed(@RequestBody CoreDataNsisAllowed nsisAllowed) {
		try {
			coreDataService.loadNsisUsers(nsisAllowed);

			coreDataLogService.addLog("/api/coredata/nsisallowed/load", nsisAllowed.getDomain(), null);

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for NSIS allowed load", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
	
	@PostMapping("/api/coredata/transfertonemlogin/load")
	public ResponseEntity<?> loadTransferToNemlogin(@RequestBody CoreDataNemLoginAllowed transferToNemlogin) {
		try {
			coreDataService.loadTransferToNemlogin(transferToNemlogin);

			coreDataLogService.addLog("/api/coredata/transfertonemlogin/load", transferToNemlogin.getDomain(), null);

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for transfer to Nemlogin load", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata")
	public ResponseEntity<?> getByDomain(@RequestParam String domain, @RequestParam(name = "onlyNsisAllowed", defaultValue = "false") boolean onlyNsisAllowed) {
		try {
			CoreData coreData = coreDataService.getByDomain(domain, onlyNsisAllowed);

			return ResponseEntity.ok(coreData);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getByDomain", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata/cprLookup")
	public ResponseEntity<?> getByCpr(@RequestParam(name = "userId") String userId, @RequestParam(name = "domain") String domain) {
		try {
			CoreDataEntryLight coreDataEntryLight = coreDataService.getByCpr(userId, domain);
			if (coreDataEntryLight == null) {
				return ResponseEntity.notFound().build();
			}

			return ResponseEntity.ok(coreDataEntryLight);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getByCpr", ex);

			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata/nemloginStatus")
	public ResponseEntity<?> getNemloginStatus(@RequestParam String domain) {
		try {
			CoreDataNemLoginStatus coreData = coreDataService.getNemloginStatus(domain);

			return ResponseEntity.ok(coreData);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getNemloginStatus", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata/status")
	public ResponseEntity<?> getStatusByDomain(@RequestParam String domain, @RequestParam(name = "onlyNsisAllowed", defaultValue = "false") boolean onlyNsisAllowed) {
		try {
			CoreDataStatus byDomain = coreDataService.getStatusByDomain(domain, onlyNsisAllowed);
			return ResponseEntity.ok(byDomain);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getStatusByDomain", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata/{cpr}")
	public ResponseEntity<?> getByDomainAndCpr(@RequestParam String domain, @PathVariable("cpr") String cpr) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}

			CoreData byDomainAndCpr = coreDataService.getByDomainAndCpr(oDomain, cpr);
			return ResponseEntity.ok(byDomainAndCpr);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getByDomainAndCpr", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@DeleteMapping("/api/coredata")
	public ResponseEntity<?> delete(@RequestBody CoreDataDelete coreDataDelete) {
		if (coreDataDelete == null) {
			return new ResponseEntity<>("Requestbody cannot be null", HttpStatus.BAD_REQUEST);
		}

		try {
			coreDataService.lockDataset(coreDataDelete);
			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for delete", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
	
	@DeleteMapping("/api/coredata/cleanup")
	public ResponseEntity<?> deleteFully(@RequestBody CoreDataDelete coreDataDelete) {
		if (coreDataDelete == null) {
			return new ResponseEntity<>("Requestbody cannot be null", HttpStatus.BAD_REQUEST);
		}

		try {
			coreDataService.deleteDataset(coreDataDelete);
			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for physical delete", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
	
	// KOMBIT Jobfunktionsrolle operations

	// OBS!!! the optional uuid that can be used to identify a user is an external uuid that must be loaded into attributes as azureId
	@PostMapping("/api/coredata/jfr/full")
	public ResponseEntity<?> fullLoadKombitJfr(@RequestBody CoreDataFullJfr coreData) {
		try {
			// validate first
			if (coreData == null || coreData.getEntryList() == null) {
				return new ResponseEntity<>("Body was null or missing entryList", HttpStatus.BAD_REQUEST);
			}

			if (coreData.getEntryList().stream().anyMatch(e -> (!StringUtils.hasLength(e.getSamAccountName()) && !StringUtils.hasLength(e.getUuid())))) {
				return new ResponseEntity<>("Missing sAMAccountName and uuid on entry. Needs at least one", HttpStatus.BAD_REQUEST);
			}

			coreDataService.loadFullKombitJfr(coreData);
			
			coreDataLogService.addLog("/api/coredata/jfr/full", coreData.getDomain(), null);
			
			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for fullLoadKombitJfr", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
	
	// OBS!!! the optional uuid that can be used to identify a user is an external uuid that must be loaded into attributes as azureId
	
	@PostMapping("/api/coredata/jfr/delta")
	public ResponseEntity<?> deltaLoadKombitJfr(@RequestBody CoreDataDeltaJfr coreData) {
		try {
			// validate first
			if (coreData == null || coreData.getEntryList() == null) {
				return new ResponseEntity<>("Body was null or missing entryList", HttpStatus.BAD_REQUEST);
			}

			if (coreData.getEntryList().stream().anyMatch(e -> (!StringUtils.hasLength(e.getSamAccountName()) && !StringUtils.hasLength(e.getUuid())))) {
				return new ResponseEntity<>("Missing sAMAccountName and uuid on entry. Needs at least one", HttpStatus.BAD_REQUEST);
			}

			coreDataService.loadDeltaKombitJfr(coreData);
			
			coreDataLogService.addLog("/api/coredata/jfr/delta", coreData.getDomain(), null);
			
			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for deltaLoadKombitJfr", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/api/coredata/passwordchange/force")
	public ResponseEntity<?> forceChangePassword(@RequestBody CoreDataForceChangePassword coreData) {
		try {
			// validate
			if (coreData == null || !StringUtils.hasLength(coreData.getDomain()) || !StringUtils.hasLength(coreData.getSamAccountName())) {
				return new ResponseEntity<>("Missing samAccountName or domain", HttpStatus.BAD_REQUEST);
			}

			boolean success = coreDataService.setForceChangePassword(coreData);
			coreDataLogService.addLog("/api/coredata/passwordchange/force", coreData.getDomain(), null);

			return success ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body("No person found");
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for forceChangePassword", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata/jfr")
	public ResponseEntity<?> getJFRByDomain(@RequestParam String domain) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}

			CoreDataFullJfr jfrByDomain = coreDataService.getJFRByDomain(oDomain);
			return ResponseEntity.ok(jfrByDomain);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getJFRByDomain", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata/jfr/{cpr}")
	public ResponseEntity<?> getJFRByCPRandDomain(@RequestParam String domain, @PathVariable("cpr") String cpr) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}

			CoreDataFullJfr jfrByDomainAndCpr = coreDataService.getJFRByDomainAndCpr(oDomain, cpr);
			return ResponseEntity.ok(jfrByDomainAndCpr);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getJFRByCPRandDomain", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}


	@GetMapping("/api/coredata/groups")
	public ResponseEntity<?> getGroupsByDomain(@RequestParam String domain) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}

			CoreDataGroupLoad groupsByDomain = coreDataService.getGroupsByDomain(oDomain);
			return ResponseEntity.ok(groupsByDomain);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getGroupsByDomain", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata/groups/{cpr}")
	public ResponseEntity<?> getGroupsByDomain(@RequestParam String domain, @PathVariable("cpr") String cpr) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}

			CoreDataGroupLoad groupsByDomainAndCpr = coreDataService.getGroupsByDomainAndCpr(oDomain, cpr);
			return ResponseEntity.ok(groupsByDomainAndCpr);
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for getGroupsByDomain", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	record GroupPersonMappingRecord(String groupUuid, String sAMAccountName) {};
	
	@PutMapping("/api/coredata/groups/single")
	public ResponseEntity<?> addPersonToGroup(@RequestParam String domain, @RequestBody GroupPersonMappingRecord body) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}
			
			coreDataService.addPersonToGroup(oDomain, body.groupUuid, body.sAMAccountName);
			
			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for addPersonToGroup", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@DeleteMapping("/api/coredata/groups/single")
	public ResponseEntity<?> removePersonFromGroup(@RequestParam String domain, @RequestBody GroupPersonMappingRecord body) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}

			coreDataService.deletePersonFromGroup(oDomain, body.groupUuid, body.sAMAccountName);
			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for removePersonFromGroup", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/api/coredata/kombitAttributes/single")
	public ResponseEntity<?> loadKombitAttributesSingle(@RequestParam String domain, @RequestBody CoreDataKombitAttributeEntry kombitAttribute) {
		try {
			coreDataService.loadKombitAttributesSingle(domain, kombitAttribute);

			return ResponseEntity.ok().build();
		}
		catch (Exception ex) {
			log.error("Failed to parse payload for KombitAttribute single load", ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
	
	@GetMapping("/api/coredata/nemlogin/lookup/{userId}")
	public ResponseEntity<?> lookupNemLogin(@RequestParam String domain, @PathVariable("userId") String userId) {
		try {
			Domain oDomain = domainService.getByName(domain);
			if (oDomain == null) {
				return new ResponseEntity<>("Unknown domain: " + domain, HttpStatus.BAD_REQUEST);
			}

			CoreDataExtendedLookup result = coreDataService.lookupExtended(oDomain, userId);
			if (result == null) {
				return ResponseEntity.notFound().build();
			}

			return ResponseEntity.ok().body(result);
		}
		catch (Exception ex) {
			log.error("Failed to lookup in NemLogin for " + userId, ex);
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
}
