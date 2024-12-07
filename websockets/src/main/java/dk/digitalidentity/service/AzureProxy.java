package dk.digitalidentity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import dk.digitalidentity.api.dto.PasswordRequest;
import dk.digitalidentity.api.dto.PasswordResponse;
import dk.digitalidentity.api.dto.UnlockRequest;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Service
public class AzureProxy {
	private RestClient restClient = null;

	@Autowired
	private OS2faktorConfiguration configuration;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		if (configuration.getAzureProxy().isEnabled()) {
			restClient = RestClient.builder()
			  .requestFactory(new HttpComponentsClientHttpRequestFactory())
			  .defaultHeader("ApiKey", configuration.getApiKey())
			  .build();
		}
	}
	
	public PasswordResponse validatePassword(PasswordRequest request) {
		if (!configuration.getAzureProxy().isEnabled()) {
			throw new UnsupportedOperationException("feature not enabled");
		}
		
		return restClient.post()
			.uri(configuration.getAzureProxy().getUrl() + "/api/validatePassword")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.toEntity(PasswordResponse.class)
			.getBody();
	}

	public PasswordResponse setPassword(PasswordRequest request) {
		if (!configuration.getAzureProxy().isEnabled()) {
			throw new UnsupportedOperationException("feature not enabled");
		}

		return restClient.post()
				.uri(configuration.getAzureProxy().getUrl() + "/api/setPassword")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toEntity(PasswordResponse.class)
				.getBody();
	}
	
	public PasswordResponse setPasswordWithForcedChange(PasswordRequest request) {
		if (!configuration.getAzureProxy().isEnabled()) {
			throw new UnsupportedOperationException("feature not enabled");
		}

		return restClient.post()
				// TODO: implement extra endpoint before we can call it (withForcedChange)
				.uri(configuration.getAzureProxy().getUrl() + "/api/setPassword")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toEntity(PasswordResponse.class)
				.getBody();
	}
	
	public int activeSessions(String domain) {
		if (!configuration.getAzureProxy().isEnabled()) {
			throw new UnsupportedOperationException("feature not enabled");
		}

		return restClient.get()
				.uri(configuration.getAzureProxy().getUrl() + "/api/sessions?domain=" + domain)
				.retrieve()
				.toEntity(Integer.class)
				.getBody();
	}

	public PasswordResponse unlockAccount(UnlockRequest request) {
		throw new UnsupportedOperationException();
	}

	public PasswordResponse passwordExpires(UnlockRequest request) {
		throw new UnsupportedOperationException();
	}
}
