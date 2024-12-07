package dk.digitalidentity.api;

import java.util.Objects;

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
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.AzureProxy;
import dk.digitalidentity.service.PasswordService;
import jakarta.validation.Valid;

@RestController
public class PasswordApi {

	@Autowired
	private PasswordService passwordService;
	
	@Autowired
	private AzureProxy azureProxy;
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@PostMapping("/api/validatePassword")
	public ResponseEntity<?> validatePassword(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}
		
		PasswordResponse response = null;
		if (configuration.getAzureProxy().isEnabled() && Objects.equals(configuration.getAzureProxy().getDomain(), request.getDomain())) {
			response = azureProxy.validatePassword(request);
		}
		else {
			response = passwordService.validatePassword(request);
		}
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping("/api/setPassword")
	public ResponseEntity<?> setPassword(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = null;
		if (configuration.getAzureProxy().isEnabled() && Objects.equals(configuration.getAzureProxy().getDomain(), request.getDomain())) {
			response = azureProxy.setPassword(request);
		}
		else {
			response = passwordService.setPassword(request);
		}
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping("/api/setPasswordWithForcedChange")
	public ResponseEntity<?> setPasswordWithForcedChange(@Valid @RequestBody PasswordRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = null;
		if (configuration.getAzureProxy().isEnabled() && Objects.equals(configuration.getAzureProxy().getDomain(), request.getDomain())) {
			response = azureProxy.setPasswordWithForcedChange(request);
		}
		else {
			response = passwordService.setPasswordWithForcedChange(request);
		}
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping("/api/unlockAccount")
	public ResponseEntity<?> unlockAccount(@Valid @RequestBody UnlockRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = null;
		if (configuration.getAzureProxy().isEnabled() && Objects.equals(configuration.getAzureProxy().getDomain(), request.getDomain())) {
			response = azureProxy.unlockAccount(request);
		}
		else {
			response = passwordService.unlockAccount(request);
		}
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/api/passwordExpires")
	public ResponseEntity<?> passwordExpires(@Valid @RequestBody UnlockRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(bindingResult.getAllErrors(), HttpStatus.BAD_REQUEST);
		}

		PasswordResponse response = null;
		if (configuration.getAzureProxy().isEnabled() && Objects.equals(configuration.getAzureProxy().getDomain(), request.getDomain())) {
			response = azureProxy.passwordExpires(request);
		}
		else {
			response = passwordService.passwordExpires(request);
		}

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/api/sessions")
	public ResponseEntity<?> activeSessions(@RequestParam(value = "domain", required = false) String domain) {
		int activeSessions = 0;
		if (configuration.getAzureProxy().isEnabled() && Objects.equals(configuration.getAzureProxy().getDomain(), domain)) {
			activeSessions = azureProxy.activeSessions(domain);
		}
		else {
			activeSessions = passwordService.activeSessions(domain);
		}

		return new ResponseEntity<>(activeSessions, HttpStatus.OK);
	}
}
