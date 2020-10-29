package dk.digitalidentity.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.CoreData;
import dk.digitalidentity.service.CoreDataService;

@RestController
public class CoreDataApi {

	@Autowired
	private CoreDataService coreDataService;

	@Transactional(rollbackFor = Exception.class)
	@PostMapping("/api/coredata/full")
	public ResponseEntity<?> fullLoad(@RequestBody CoreData coreData) {
		return coreDataService.load(coreData, true);
	}

	@Transactional(rollbackFor = Exception.class)
	@PostMapping("/api/coredata/delta")
	public ResponseEntity<?> deltaLoad(@RequestBody CoreData coreData) {
		return coreDataService.load(coreData, false);
	}
}
