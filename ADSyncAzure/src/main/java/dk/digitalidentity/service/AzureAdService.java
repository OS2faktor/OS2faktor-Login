package dk.digitalidentity.service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.http.client.HttpResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.NotAcceptableStatusException;

import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.config.modules.AzureAd;
import dk.digitalidentity.security.BearerToken;
import dk.digitalidentity.security.TokenFetcher;
import dk.digitalidentity.service.dto.CoreData;
import dk.digitalidentity.service.dto.CoreDataEntry;
import dk.digitalidentity.util.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AzureAdService {
//    private String deltaLink;

    @Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CoreDataService coreDataService;

    @Autowired
    private TokenFetcher tokenFetcher;

    public void fullSync() throws Exception {
        String url = configuration.getAzureAd().getBaseUrl() + configuration.getAzureAd().getApiVersion() + "/users?$count=true&$top=500&" + getFields();
        
        // Fetch all entries and set deltaLinkUrl For delta sync
        CoreData coreData = getCoreData(url, tokenFetcher.getToken());

        if (coreData != null) {
            coreDataService.sendData(coreData, true);
        }
    }

    /*
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
    */

    @SuppressWarnings("unchecked")
    private CoreData getCoreData(String url, BearerToken token) throws Exception {
        List<CoreDataEntry> coreDataEntries = new ArrayList<>();
        boolean morePages = true;

        while (morePages) {
            Map<String, Object> responseBody = getFromAzureAd(url, token);

            if (!responseBody.containsKey("value")) {
                LinkedHashMap<String, String> error = (LinkedHashMap<String, String>) responseBody.get("error");
                String message = "An error occurred while getting core data";
                if (error != null && error.containsKey("message")) {
                    message = error.get("message");
                }

                throw new Exception(message);
            }

            coreDataEntries.addAll(convertToCoreDataFormat((ArrayList<LinkedHashMap<String, Object>>) responseBody.get("value")));
            
            if (responseBody.containsKey("@odata.nextLink")) {
                url = (String) responseBody.get("@odata.nextLink");
            }
            /*
            else if (responseBody.containsKey("@odata.deltaLink")) {
                // All entries has been fetched, save deltaLink for next deltaSave
                deltaLink = (String) responseBody.get("@odata.deltaLink");
                morePages = false;
            }
            */
            else {
            	morePages = false;
                //throw new Exception("No Delta or next link in response from AzureAD");
            }
        }

        if (!coreDataEntries.isEmpty()) {

        	// filter on group if needed
        	if (StringUtils.hasText(configuration.getAzureAd().getFilterGroupId())) {
                List<String> filteredUsers = fetchAllAllowedUsers(token);
                Iterator<CoreDataEntry> iterator = coreDataEntries.iterator();

                while (iterator.hasNext()) {
                	CoreDataEntry entry = iterator.next();

                	if (!filteredUsers.contains(entry.getAzureInternalId())) {
                		iterator.remove();
                	}
                }        		
        	}

        	// fetch NSIS allowed
        	if (StringUtils.hasText(configuration.getAzureAd().getNsisAllowedGroupId()) && StringUtils.hasText(configuration.getAzureAd().getTransferToNemloginGroupId())) {
	            List<String> nsisAllowedIds = fetchAllNsisAllowedUsers(token);
	            List<String> transferToNemloginIds = fetchAllTransferToNemloginUsers(token);

	            for (CoreDataEntry coreDataEntry : coreDataEntries) {
	                coreDataEntry.setNsisAllowed(nsisAllowedIds.contains(coreDataEntry.getAzureInternalId()));
	                coreDataEntry.setTransferToNemlogin(transferToNemloginIds.contains(coreDataEntry.getAzureInternalId()));
	            }
        	}

            CoreData coreData = new CoreData();
            coreData.setEntryList(coreDataEntries);
            coreData.setDomain(configuration.getCoreData().getDomain());

            log.info("Found " + coreDataEntries.size() + " CoreData entries");

            return coreData;
        }

        return null;
    }

	private List<String> fetchAllNsisAllowedUsers(BearerToken token) throws Exception {
        log.info("Fetching all transitive members of the NsisAllowed group");
        return getUUIDsOfTransitiveMembers(configuration.getAzureAd().getNsisAllowedGroupId(), token);
    }

	private List<String> fetchAllAllowedUsers(BearerToken token) throws Exception {
        log.info("Fetching all transitive members of the allowed group");
        return getUUIDsOfTransitiveMembers(configuration.getAzureAd().getFilterGroupId(), token);
    }

    private List<String> fetchAllTransferToNemloginUsers(BearerToken token) throws Exception {
        log.info("Fetching all transitive members of the TransferToNemlogin group");
        return getUUIDsOfTransitiveMembers(configuration.getAzureAd().getTransferToNemloginGroupId(), token);
    }

    @SuppressWarnings("unchecked")
    private List<String> getUUIDsOfTransitiveMembers(String groupUuid, BearerToken token) throws Exception {
        if (!StringUtils.hasLength(groupUuid)) {
            log.info("Group UUID was null or empty, returning empty list");
            return new ArrayList<>();
        }


        AzureAd adConfig = configuration.getAzureAd();
        String url = adConfig.getBaseUrl() + adConfig.getApiVersion() + "/groups/" + groupUuid + "/transitiveMembers?$select=odata.type,id&$top=999";

        List<String> result = new ArrayList<>();
        boolean morePages = true;
        while (morePages) {
            Map<String, Object> responseBody = getFromAzureAd(url, token);

            // Validation
            if (!responseBody.containsKey("value")) {
                LinkedHashMap<String, String> error = (LinkedHashMap<String, String>) responseBody.get("error");
                String message = "An error occurred trying to fetch all users from group with uuid: " + groupUuid;
                if (error != null && error.containsKey("message")) {
                    message = error.get("message");
                }

                throw new Exception(message);
            }

            // Parse data
            ArrayList<LinkedHashMap<String, String>> value = (ArrayList<LinkedHashMap<String, String>>) responseBody.get("value");
            if (value != null && !value.isEmpty()) {
                // Ignore any member other than users, and map to a list of Ids
                result.addAll(
                        value.stream()
                                .filter(map -> "#microsoft.graph.user".equals(map.get("@odata.type")))
                                .map(map -> map.get("id"))
                                .collect(Collectors.toList())
                );
            }

            // Set nextLink or stop looking
            if (responseBody.containsKey("@odata.nextLink")) {
                url = java.net.URLDecoder.decode((String) responseBody.get("@odata.nextLink"), StandardCharsets.UTF_8.name());
            }
            else {
                morePages = false;
            }
        }

        return result;
    }

    private Map<String, Object> getFromAzureAd(String url, BearerToken token) throws HttpResponseException, NotAcceptableStatusException {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Constants.Authorization, "Bearer " + token.getAccessToken());

        HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(URI.create(url), HttpMethod.GET, request, new ParameterizedTypeReference<>() { });

        if (response.getStatusCode().isError()) {
            throw new HttpResponseException(response.getStatusCodeValue(), response.getStatusCode().getReasonPhrase());
        }

        // Handle error and extract message if present
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new NotAcceptableStatusException("Response body from AzureAD was null");
        }

        return responseBody;
    }

    @SuppressWarnings("unchecked")
    private String extractCpr(Map<String, Object> responseBody) {
    	if (!configuration.isExtractCustomCpr()) {
    		return null;
    	}

        Object rawAttrs = responseBody.get("customSecurityAttributes");
        if (!(rawAttrs instanceof Map)) {
            return null; // no attributes, or caller lacks the Attribute Assignment Reader role
        }

        Object set = ((Map<String, Object>) rawAttrs).get(configuration.getCustomCprCollectionName());
        if (!(set instanceof Map)) {
            return null;
        }

        Object cpr = ((Map<String, Object>) set).get(configuration.getCustomCprFieldName());

        if (cpr instanceof List) {
            List<Object> values = (List<Object>) cpr;
            if (values.isEmpty() || values.get(0) == null) {
                return null;
            }
            return values.get(0).toString();
        }

        if (cpr instanceof String) {
            return (String) cpr;
        }

        return null;
    }

    private List<CoreDataEntry> convertToCoreDataFormat(ArrayList<LinkedHashMap<String, Object>> userList) {
        AzureAd adConfig = configuration.getAzureAd();
        List<CoreDataEntry> entries = new ArrayList<>();

        if (userList.isEmpty()) {
            return entries;
        }

        for (LinkedHashMap<String, Object> userMap : userList) {
            CoreDataEntry entry = new CoreDataEntry();

            // Skip deleted entries, these will only be dealt with when doing a full sync
            if (userMap.containsKey("@removed")) {
                continue;
            }

            // Skip users with nothing in the CPR field (same as AD integration)
            String cpr = null;
            
            if (configuration.isExtractCustomCpr()) {
            	cpr = extractCpr(userMap);
            }
            else {
                cpr = (String) userMap.get(adConfig.getCprField());            	
            }

            String uuid = toUUID((String) userMap.get("onPremisesImmutableId"));
            if (uuid == null) {
//            	log.warn("Skipping id=" + userMap.get("id") + " due to missing onPremisesImmutableId");
            	continue;
            }

            if (!StringUtils.hasLength(cpr)) {
            	log.debug("No cpr for " + uuid);
                continue;
            }
            
            // fix bad formatting
            cpr = cpr.trim().replace("-", "");
            if (cpr.length() != 10) {
            	log.warn("User uuid=" + uuid + " has an invalid formatted CPR number");
            	continue;
            }
            
            String sAMAccountName = (String) userMap.get(adConfig.getSAMAccountNameField());
            if (sAMAccountName == null || (StringUtils.hasText(adConfig.getUpn()) && !sAMAccountName.endsWith(adConfig.getUpn()))) {
            	continue;
            }
            sAMAccountName = sAMAccountName.split("@")[0];

            // Default fields
            entry.setAzureInternalId((String) userMap.get("id"));
            entry.setUuid(uuid);
            entry.setCpr(cpr);
            entry.setEmail((String) userMap.getOrDefault("mail", ""));
            entry.setSamAccountName(sAMAccountName);
            entry.setNsisAllowed(false);
            entry.setTransferToNemlogin(false);

            // Compute name
            String displayName = (String) userMap.get("displayName");
            if (!StringUtils.hasLength(displayName)) {
                displayName = (String) userMap.get("givenName") + " " + (String) userMap.get("surname");
            }
            entry.setName(displayName);

            // Add attribute map for configurable attributes
            Map<String, String> attributes = adConfig.getAttributes();
            if (attributes != null && !attributes.isEmpty()) {
                HashMap<String, String> attributeMap = new HashMap<>();
                for (Map.Entry<String, String> pair : attributes.entrySet()) {
                    if (userMap.containsKey(pair.getValue())) {
                        attributeMap.put(pair.getKey(), (String) userMap.get(pair.getValue()));
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
        sb.append("onPremisesImmutableId,");
        sb.append(adConfig.getSAMAccountNameField());

        // Add configured attributes
        // Values in attribute map should correspond to attributes in AD
        Map<String, String> attributes = adConfig.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            for (String value : attributes.values()) {
                if (StringUtils.hasLength(value)) {
                    sb.append(",").append(value);
                }
            }
        }

        return sb.toString();
    }
    
	private static String toUUID(String base64Encoded) {
		if (base64Encoded == null) {
			return null;
		}
		
		try {
			byte[] binaryEncoding = Base64.getDecoder().decode(base64Encoded);
			ByteBuffer source = ByteBuffer.wrap(binaryEncoding);
			ByteBuffer target = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(source.getInt()).putShort(source.getShort()).putShort(source.getShort()).order(ByteOrder.BIG_ENDIAN).putLong(source.getLong());
	
			target.rewind();
	
			return new UUID(target.getLong(), target.getLong()).toString();
		}
		catch (Exception ex) {
			log.warn("Failed to decode: " + base64Encoded);
			return null;
		}
	}
}
