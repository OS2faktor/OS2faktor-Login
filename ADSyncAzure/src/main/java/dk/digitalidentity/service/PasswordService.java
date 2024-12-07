package dk.digitalidentity.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.api.dto.PasswordRequest;
import dk.digitalidentity.api.dto.PasswordResponse;
import dk.digitalidentity.api.dto.PasswordResponse.PasswordStatus;
import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.config.modules.AzureAd;
import dk.digitalidentity.security.TokenFetcher;
import dk.digitalidentity.service.dto.SetPassword;
import dk.digitalidentity.util.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    @Autowired
    private TokenFetcher tokenFetcher;

	public PasswordResponse setPassword(PasswordRequest request) {
    	PasswordResponse response = new PasswordResponse();
    	response.setStatus(PasswordStatus.TECHNICAL_ERROR);

        try {
            if (!StringUtils.hasLength(request.getUserUuid())) {
                throw new Exception("uuid was null in request");
            }

            String url = configuration.getAzureAd().getBaseUrl() + configuration.getAzureAd().getApiVersion() + "/users/" + request.getUserUuid();

            HttpHeaders headers = new HttpHeaders();
            headers.set(Constants.Authorization, "Bearer " + tokenFetcher.getToken().getAccessToken());
            headers.set("Content-Type", "application/json");

            SetPassword setPassword = new SetPassword();
            setPassword.getPasswordProfile().setPassword(request.getPassword());
            
            HttpEntity<SetPassword> req = new HttpEntity<>(setPassword, headers);

            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.PATCH, req, String.class);
            if (res.getStatusCodeValue() >= 200 && res.getStatusCodeValue() <= 299) {
            	response.setStatus(PasswordStatus.OK);
            }
            else {
            	response.setMessage("HTTP error: " + res.getStatusCodeValue() + " / " + res.getBody());
            }            
        }
        catch (Exception ex) {
        	log.error("Failed to set password", ex);
        	response.setMessage("Exception: " + ex.getMessage());
        }
        
        return response;
	}

    public PasswordResponse validatePassword(PasswordRequest passwordRequest) {
    	PasswordResponse adPasswordResponse = new PasswordResponse();
    	adPasswordResponse.setStatus(PasswordStatus.TECHNICAL_ERROR);
        AzureAd adConfig = configuration.getAzureAd();

        try {

            // Before we can even try the login we need to be able to find the username of the person we are trying to verify.
        	String userPrincipalName = passwordRequest.getUserName();
        	if (!userPrincipalName.contains("@")) {
        		userPrincipalName = passwordRequest.getUserName() + adConfig.getUpn();
        	}

            // Use acquired userPrincipalName to validate password
            String url = adConfig.getLoginBaseUrl() + adConfig.getTenantID() + "/oauth2/v2.0/token";

            // Create body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add(Constants.Scope, "https://graph.microsoft.com/.default");
            body.add(Constants.GrantType, "password");
            body.add(Constants.ClientId, adConfig.getClientID());
            body.add(Constants.ClientSecret, adConfig.getClientSecret());
            body.add(Constants.UserName, userPrincipalName);
            body.add(Constants.UserPassword, passwordRequest.getPassword());

            // Create headers and request
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Validate password against Azure AD
            try {
                ResponseEntity<Map<String, String>> response = restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<>() { });

                Map<String, String> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("access_token")) {
                    if (StringUtils.hasLength(responseBody.get("access_token"))) {
                        adPasswordResponse.setStatus(PasswordStatus.OK);
                    }
                }
            }
            catch (RestClientException exception) {
                String errorMessage = exception.getMessage();
                if (StringUtils.hasLength(errorMessage)) {
                	// we need the multi-factor authentication check first - it also contains "invalid_grant",
                	// and we want to make sure it gets mapped to a technical error
                	if (errorMessage.contains("multi-factor authentication")) {
                		log.error("Password validation failed: " + errorMessage);
                	}
                	else if (errorMessage.contains("invalid_grant")) {
                		log.debug("Failure reason: " + errorMessage);

                    	adPasswordResponse.setStatus(PasswordStatus.FAILURE);
                    }
                	else {
                		log.error("Password validation failed: " + errorMessage);
                	}
                }
            }
            
            return adPasswordResponse;
        }
        catch (Exception ex) {
    		log.error("Unexpected exception during password validation", ex);

            adPasswordResponse.setMessage(ex.getMessage());

            return adPasswordResponse;
        }
    }
}
