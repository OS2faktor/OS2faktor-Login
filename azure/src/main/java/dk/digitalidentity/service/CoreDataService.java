package dk.digitalidentity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.service.dto.CoreData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class CoreDataService {

    @Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    @Autowired
    private RestTemplate restTemplate;

    @SneakyThrows
    public void sendData(CoreData coreData, boolean fullLoad) throws RestClientResponseException {
        // Determine URL
        String baseUrl = configuration.getCoreData().getBaseUrl();
        String url = baseUrl + "/api/coredata/" + (fullLoad ? "full" : "delta");

        // Add headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("apiKey", configuration.getCoreData().getApiKey());
        HttpEntity<CoreData> request = new HttpEntity<>(coreData, headers);

        if (log.isDebugEnabled()) {
            ObjectMapper mapper = new ObjectMapper();
            log.debug("===========================request begin=============================================");
            log.debug("URI         : {}", url);
            log.debug("Headers     : {}", request.getHeaders());
            log.debug("Request body: {}", mapper.writeValueAsString(request.getBody()));
            log.debug("==========================request end================================================");
        }

        // Send Post
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Handle errors
        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            String message = response.getBody() != null ? response.getBody() : "<empty>";
            HttpStatus statusCode = response.getStatusCode();
            log.error("Error sending data: Code=" + statusCode + " Body=" + message);
            throw new RestClientResponseException(message, statusCode.value(), statusCode.getReasonPhrase(), null, null, null);
        }
    }
}
