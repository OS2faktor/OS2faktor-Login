package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    	response.setValid(false);

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
            	response.setValid(true);
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
        adPasswordResponse.setValid(false);

        // Fetch configuration
        AzureAd adConfig = configuration.getAzureAd();
        String baseUrl = adConfig.getBaseUrl();
        String apiVersion = adConfig.getApiVersion();

        try {
        	/*
            if (!StringUtils.hasLength(passwordRequest.getUserUuid())) {
                throw new Exception("uuid was null in request");
            }
            */

            // Before we can even try the login we need to be able to find the username of the person we are trying to verify.
//            String userPrincipalName = getUserPrincipalName(passwordRequest.getUserUuid(), baseUrl, apiVersion);
        	String userPrincipalName = passwordRequest.getUserName() + adConfig.getUpn();

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
                        adPasswordResponse.setValid(true);
                    }
                }
            }
            catch (RestClientException exception) {
                String errorMessage = exception.getMessage();
                if (StringUtils.hasLength(errorMessage)) {
                    // Ignore invalid grant, this just means that password or username was wrong, not an error
                    if (!errorMessage.contains("invalid_grant")) {
                        adPasswordResponse.setMessage(errorMessage);
                    }
                }
            }
            
            return adPasswordResponse;
        }
        catch (Exception ex) {
            adPasswordResponse.setMessage(ex.getMessage());
            return adPasswordResponse;
        }
    }

    @SuppressWarnings("unchecked")
	private String getUserPrincipalName(String uuid, String baseUrl, String apiVersion) throws Exception {
        String url = baseUrl + "/" + apiVersion + "/users?$select=id,userPrincipalName&$filter=id eq '" + uuid + "'";

        HttpHeaders headers = new HttpHeaders();
        headers.set(Constants.Authorization, "Bearer " + tokenFetcher.getToken().getAccessToken());

        HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<>() { });

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new Exception("Response body from AzureAD was null");
        }

        if (!responseBody.containsKey("value")) {
            LinkedHashMap<String, String> error = (LinkedHashMap<String, String>) responseBody.get("error");
            String message = "An error occurred trying to convert from uuid to UPN";
            if (error != null && error.containsKey("message")) {
                message = error.get("message");
            }

            throw new Exception(message);
        }

        ArrayList<LinkedHashMap<String, String>> value = (ArrayList<LinkedHashMap<String, String>>) responseBody.get("value");
        if (value == null) {
            throw new Exception("UserQuery response value was null");
        }

        if (value.size() == 1) {
            LinkedHashMap<String, String> user = value.get(0);
            if (!user.containsKey("userPrincipalName")) {
                throw new Exception("User object did not have a userPrincipalName field");
            }

            return user.get("userPrincipalName");
        }
        else {
            throw new Exception("Returned list of users had wrong size: " + value.size());
        }
    }
}
