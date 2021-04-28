package dk.digitalidentity.security;

import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.config.modules.AzureAd;
import dk.digitalidentity.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class TokenFetcher {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    private BearerToken token;

    public BearerToken getToken() {
        // Use previous token if not about to expire
        if (token != null && token.getExpiryTimestamp().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return token;
        }

        AzureAd adConfig = configuration.getAzureAd();

        // Url
        String url = adConfig.getLoginBaseUrl() + adConfig.getTenantID() + "/oauth2/v2.0/token";

        // Create body
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add(Constants.Scope, "https://graph.microsoft.com/.default");
        body.add(Constants.GrantType, "client_credentials");
        body.add(Constants.ClientId, adConfig.getClientID());
        body.add(Constants.ClientSecret, adConfig.getClientSecret());

        // Create headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/x-www-form-urlencoded");

        // Create and send request
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, String>> response = restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});

        return new BearerToken(response.getBody());
    }
}
