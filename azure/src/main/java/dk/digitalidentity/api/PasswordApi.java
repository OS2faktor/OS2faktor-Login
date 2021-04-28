package dk.digitalidentity.api;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.PasswordRequest;
import dk.digitalidentity.api.dto.PasswordResponse;
import dk.digitalidentity.service.PasswordService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PasswordApi {

	@Autowired
	private PasswordService passwordService;

	@PostMapping("/api/validatePassword")
	public ResponseEntity<?> validatePassword(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		log.info("Validating password for " + request.toString());

		if (bindingResult.hasErrors()) {
			log.warn("Rejecting validatePassword request due to invalid input: " + request.toString());
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = passwordService.validatePassword(request);

		log.info("Validating password result " + response.isValid() + " for " + request.toString());

		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping("/api/setPassword")
	public ResponseEntity<?> setPassword(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		log.info("Setting password for " + request.toString());
		
		if (bindingResult.hasErrors()) {
			log.warn("Rejecting setPassword request due to invalid input: " + request.toString());
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = passwordService.setPassword(request);

		log.info("Setting password result " + response.isValid() + " for " + request.toString());

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/api/sessions")
	public ResponseEntity<?> activeSessions(@RequestParam(value = "domain", required = false) String domain) {
		return new ResponseEntity<>(1L, HttpStatus.OK);
	}
}
