package dk.digitalidentity.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.CoreData;
import dk.digitalidentity.api.dto.CoreDataDelete;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.service.CoreDataService;

@RestController
public class CoreDataApi {

	@Autowired
	private CoreDataService coreDataService;

	@Autowired
	private DomainService domainService;

	@PostMapping("/api/coredata/full")
	public ResponseEntity<?> fullLoad(@RequestBody CoreData coreData) {
		try {
			coreDataService.load(coreData, true);
			return ResponseEntity.ok().build();
		}
		catch (IllegalArgumentException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/api/coredata/delta")
	public ResponseEntity<?> deltaLoad(@RequestBody CoreData coreData) {
		try {
			coreDataService.load(coreData, false);
			return ResponseEntity.ok().build();
		}
		catch (IllegalArgumentException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/api/coredata")
	public ResponseEntity<?> getByDomain(@RequestParam String domain) {
		try {
			CoreData byDomain = coreDataService.getByDomain(domain);
			return ResponseEntity.ok(byDomain);
		}
		catch (IllegalArgumentException ex) {
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
		catch (IllegalArgumentException ex) {
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
		catch (IllegalArgumentException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
}
