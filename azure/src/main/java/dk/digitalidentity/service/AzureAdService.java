package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.config.modules.AzureAd;
import dk.digitalidentity.security.TokenFetcher;
import dk.digitalidentity.security.BearerToken;
import dk.digitalidentity.service.dto.CoreData;
import dk.digitalidentity.service.dto.CoreDataEntry;
import dk.digitalidentity.util.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AzureAdService {
    private String deltaLink;

    @Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CoreDataService coreDataService;

    @Autowired
    private TokenFetcher tokenFetcher;

    public void fullSync() throws Exception {
        String url = configuration.getAzureAd().getBaseUrl() + configuration.getAzureAd().getApiVersion() + "/users/delta?" + getFields();
        
        // Fetch all entries and set deltaLinkUrl For delta sync
        CoreData coreData = getCoreData(url, tokenFetcher.getToken());

        if (coreData != null) {
            coreDataService.sendData(coreData, true);
        }
    }

    public void deltaSync() throws Exception {
        if (!StringUtils.hasLength(deltaLink)) {
            log.debug("Skipping DeltaSync since no deltaLink was saved");
            return;
        }

        // Fetch all entries and set next deltaLinkUrl
        CoreData coreData = getCoreData(deltaLink, tokenFetcher.getToken());

        if (coreData != null) {
            coreDataService.sendData(coreData, false);
        }
    }

    @SuppressWarnings("unchecked")
    private CoreData getCoreData(String url, BearerToken token) throws Exception {
        boolean morePages = true;
        List<CoreDataEntry> coreDataEntries = new ArrayList<>();

        while (morePages) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(Constants.Authorization, "Bearer " + token.getAccessToken());
            headers.set(Constants.MaxPageSize, "1000");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<>() { });

            // Handle error and extract message if present
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new Exception("Response body from AzureAD was null");
            }

            if (!responseBody.containsKey("value")) {
                LinkedHashMap<String, String> error = (LinkedHashMap<String, String>) responseBody.get("error");
                String message = "An error occurred while getting core data";
                if (error != null && error.containsKey("message")) {
                    message = error.get("message");
                }

                throw new Exception(message);
            }

            coreDataEntries.addAll(convertToCoreDataFormat((ArrayList<LinkedHashMap<String, String>>) responseBody.get("value")));
            
            if (responseBody.containsKey("@odata.nextLink")) {
                url = (String) responseBody.get("@odata.nextLink");
            }
            else if (responseBody.containsKey("@odata.deltaLink")) {
                // All entries has been fetched, save deltaLink for next deltaSave
                deltaLink = (String) responseBody.get("@odata.deltaLink");
                morePages = false;
            }
            else {
                throw new Exception("No Delta or next link in response from AzureAD");
            }
        }

        // Iterate all objects and set NSISAllowed on users with the configured group
        if (!coreDataEntries.isEmpty()) {
            List<String> nsisAllowedIds = fetchAllNsisAllowedUsers(token);
            for (CoreDataEntry coreDataEntry : coreDataEntries) {
                coreDataEntry.setNsisAllowed(nsisAllowedIds.contains(coreDataEntry.getUuid()));
            }

            CoreData coreData = new CoreData();
            coreData.setEntryList(coreDataEntries);
            coreData.setDomain(configuration.getCoreData().getDomain());

            log.info("Found " + coreDataEntries.size() + " CoreData entries");

            return coreData;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
	private List<String> fetchAllNsisAllowedUsers(BearerToken token) throws Exception {
        AzureAd adConfig = configuration.getAzureAd();
        String url = adConfig.getBaseUrl() + adConfig.getApiVersion() + "/groups/" + adConfig.getNsisAllowedGroupId() + "/transitiveMembers?$select=odata.type,id";

        // Build request
        HttpHeaders headers = new HttpHeaders();
        headers.set(Constants.Authorization, "Bearer " + token.getAccessToken());

        HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<>() { });

        // Handle error and extract message if present
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new Exception("Response body from AzureAD was null");
        }

        if (!responseBody.containsKey("value")) {
            LinkedHashMap<String, String> error = (LinkedHashMap<String, String>) responseBody.get("error");
            String message = "An error occurred trying to fetch all NSISAllowedUsers";
            if (error != null && error.containsKey("message")) {
                message = error.get("message");
            }

            throw new Exception(message);
        }

        // Get userPrincipalName for validating password
        ArrayList<LinkedHashMap<String, String>> value = (ArrayList<LinkedHashMap<String, String>>) responseBody.get("value");
        if (value == null) {
            throw new Exception("UserQuery response value was null");
        }

        // Ignore any member other than users, and map to a list of Ids
        List<String> result = value
                .stream()
                .filter(map -> "#microsoft.graph.user".equals(map.get("@odata.type")))
                .map(map -> map.get("id"))
                .collect(Collectors.toList());

        return result;
    }

    private List<CoreDataEntry> convertToCoreDataFormat(ArrayList<LinkedHashMap<String, String>> userList) {
        AzureAd adConfig = configuration.getAzureAd();
        List<CoreDataEntry> entries = new ArrayList<>();

        if (userList.isEmpty()) {
            return entries;
        }

        for (LinkedHashMap<String, String> userMap : userList) {
            CoreDataEntry entry = new CoreDataEntry();

            // Skip deleted entries, these will only be dealt with when doing a full sync
            if (userMap.containsKey("@removed")) {
                continue;
            }

            // Skip users with nothing in the CPR field (same as AD integration)
            String cpr = userMap.get(adConfig.getCprField());
            if (!StringUtils.hasLength(cpr)) {
                continue;
            }
            
            // fix bad formatting
            cpr = cpr.trim().replace("-", "");
            if (cpr.length() != 10) {
            	log.warn("User " + userMap.get("id") + " has an invalid formatted CPR number");
            	continue;
            }
            
            String sAMAccountName = userMap.get(adConfig.getSAMAccountNameField());
            if (sAMAccountName == null || !sAMAccountName.endsWith(adConfig.getUpn())) {
            	continue;
            }
            sAMAccountName = sAMAccountName.split("@")[0];

            // Default fields
            entry.setUuid(userMap.get("id"));
            entry.setCpr(cpr);
            entry.setEmail(userMap.getOrDefault("mail", ""));
            entry.setSamAccountName(sAMAccountName);
            entry.setNsisAllowed(false);

            // Compute name
            String displayName = userMap.get("displayName");
            if (!StringUtils.hasLength(displayName)) {
                displayName = userMap.get("givenName") + " " + userMap.get("surname");
            }
            entry.setName(displayName);

            // Add attribute map for configurable attributes
            List<String> attributes = adConfig.getAttributes();
            if (attributes != null && !attributes.isEmpty()) {
                HashMap<String, String> attributeMap = new HashMap<>();
                for (String attribute : attributes) {
                    if (userMap.containsKey(attribute)) {
                        attributeMap.put(attribute, userMap.get(attribute));
                    }
                }
                if (!attributeMap.isEmpty()) {
                    entry.setAttributes(attributeMap);
                }
            }

            entries.add(entry);
        }

        return entries;
    }

    private String getFields() {
        AzureAd adConfig = configuration.getAzureAd();

        StringBuilder sb = new StringBuilder("$select=");
        // Default fields
        sb.append("id,");
        sb.append(adConfig.getCprField()).append(",");
        sb.append("displayName,");
        sb.append("givenName,");
        sb.append("surname,");
        sb.append("mail,");
        sb.append(adConfig.getSAMAccountNameField());

        // Add configured attributes
        List<String> attributes = adConfig.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            for (String attribute : attributes) {
                sb.append(",").append(attribute);
            }
        }

        return sb.toString();
    }
}
