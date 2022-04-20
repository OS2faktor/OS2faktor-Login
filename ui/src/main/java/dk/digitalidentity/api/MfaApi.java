package dk.digitalidentity.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;

@RestController
public class MfaApi {

	@Autowired
	private MFAService mfaService;

	@GetMapping("/api/mfa/clients")
	public ResponseEntity<?> getClients(@RequestParam(value = "cpr") String cpr) {		
		List<MfaClient> clients = mfaService.getClients(cpr);
		
		return new ResponseEntity<>(clients, HttpStatus.OK);
	}
}
