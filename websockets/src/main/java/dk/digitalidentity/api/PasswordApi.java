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
import dk.digitalidentity.api.dto.UnlockRequest;
import dk.digitalidentity.service.PasswordService;

@RestController
public class PasswordApi {

	@Autowired
	private PasswordService passwordService;
	
	@PostMapping("/api/validatePassword")
	public ResponseEntity<?> validatePassword(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = passwordService.validatePassword(request);
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping("/api/setPassword")
	public ResponseEntity<?> setPassword(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = passwordService.setPassword(request);
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping("/api/setPasswordWithForcedChange")
	public ResponseEntity<?> setPasswordWithForcedChange(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = passwordService.setPasswordWithForcedChange(request);
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping("/api/unlockAccount")
	public ResponseEntity<?> unlockAccount(@Valid @RequestBody UnlockRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = passwordService.unlockAccount(request);
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/api/passwordExpires")
	public ResponseEntity<?> passwordExpires(@Valid @RequestBody UnlockRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = passwordService.passwordExpires(request);

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/api/sessions")
	public ResponseEntity<?> activeSessions(@RequestParam(value = "domain", required = false) String domain) {
		int activeSessions = passwordService.activeSessions(domain);

		return new ResponseEntity<>(activeSessions, HttpStatus.OK);
	}
}
