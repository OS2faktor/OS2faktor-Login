package dk.digitalidentity.nemlogin.service;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.ResponseSpec.ErrorHandler;
import org.springframework.web.client.RestClientResponseException;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.nemlogin.service.model.TokenResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableCaching
public class NemLoginTokenCache {
	private ErrorHandler defaultClientErrorHandler;
	private ErrorHandler defaultServerErrorHandler;
	
	@Qualifier("nemLoginRestClient")
	@Autowired
	private RestClient restClient;
	
	@Autowired
	private CommonConfiguration config;
	
	public NemLoginTokenCache() {
		defaultClientErrorHandler = new ErrorHandler() {
			
			@Override
			public void handle(HttpRequest req, ClientHttpResponse res) throws IOException {
	            throw new RestClientResponseException(
                    "Client error: " + res.getStatusCode() + " : " + IOUtils.readFully(res.getBody(), res.getBody().available()),
                    res.getStatusCode(),
                    res.getStatusText(),
                    null,
                    null,
                    null
	            );
			}
		};
		
		defaultServerErrorHandler = new ErrorHandler() {
			
			@Override
			public void handle(HttpRequest req, ClientHttpResponse res) throws IOException {
	            throw new RestClientResponseException(
                    "Server error: " + res.getStatusCode() + " : " + IOUtils.readFully(res.getBody(), res.getBody().available()),
                    res.getStatusCode(),
                    res.getStatusText(),
                    null,
                    null,
                    null
	            );
			}
		};
	}

	@Cacheable(value = "token", unless = "#result == null")
	public String fetchToken() {
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/idmlogin/tls/authenticate";
		
		try {
			TokenResponse response = restClient.post()
		        .uri(url)
		        .retrieve()
		        .onStatus(HttpStatusCode::is4xxClientError, defaultClientErrorHandler)
		        .onStatus(HttpStatusCode::is5xxServerError, defaultServerErrorHandler)
		        .body(TokenResponse.class);

			return response.getAccessToken();
		}
		catch (Exception ex) {
			log.error("Failed to fetch token from nemloginApi", ex);
		}
		
		return null;
	}
	
	@CacheEvict(value = "token", allEntries = true)
	public void cleanUpToken() {
		;
	}
}
