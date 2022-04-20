package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.http.client.HttpResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.NotAcceptableStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.security.BearerToken;
import dk.digitalidentity.security.TokenFetcher;
import dk.digitalidentity.service.dto.BatchedCall;
import dk.digitalidentity.service.dto.KombitRoleResult;
import dk.digitalidentity.service.dto.UserKombitRoleResultEntry;
import dk.digitalidentity.service.dto.KombitJfr.CoreDataDeltaJfr;
import dk.digitalidentity.service.dto.KombitJfr.CoreDataDeltaJfrEntry;
import dk.digitalidentity.service.dto.KombitJfr.CoreDataFullJfr;
import dk.digitalidentity.service.dto.KombitJfr.CoreDataFullJfrEntry;
import dk.digitalidentity.service.dto.KombitJfr.Jfr;
import dk.digitalidentity.util.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KombitRoleAdService {

	@Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CoreDataService coreDataService;

    @Autowired
    private TokenFetcher tokenFetcher;


    private HashMap<String, Jfr> kombitGroups;
    private ArrayList<BatchedCall> deltaLinks;

    public void fullSync() throws Exception {

        // In case of full sync, we fetch the KOMBIT roles themselves
        this.kombitGroups = fetchKombitRoleGroups();

        // Convert KombitGroups into initial list of batch-jobs
        LinkedList<BatchedCall> batchJobs = new LinkedList<>();
        for (Map.Entry<String, Jfr> kombitRole : kombitGroups.entrySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append("/groups/delta?$filter=id eq '").append(kombitRole.getKey()).append("'&$expand=members&$select=id,members");
            batchJobs.add(new BatchedCall(sb.toString(), HttpMethod.GET.name(), kombitRole.getKey()));
        }

        // Call Azure ad and get both DeltaLinks for subsequent calls and also a list of users and their memberships
        KombitRoleResult result = queryAzureAdWithBatchJobs(batchJobs, true);

        // Save delta links for future use
        this.deltaLinks = result.getDeltaLinks();

        // Convert user groups to fit CoreData API
        CoreDataFullJfr coreData = new CoreDataFullJfr();
        coreData.setDomain(configuration.getCoreData().getDomain());
        coreData.setEntryList(
                result.getUsers().entrySet()
                        .stream()
                        .map(entry -> new CoreDataFullJfrEntry(entry.getKey(), entry.getValue().getAddRoles()))
                        .collect(Collectors.toList())
        );

        coreDataService.sendKombitRoleDataFull(coreData);
    }

    public void deltaSync() throws Exception {
        if (kombitGroups == null || kombitGroups.isEmpty()) {
            log.debug("Skipping DeltaSync. No KOMBIT roles have been fetched");
            return;
        }

        if (deltaLinks == null || deltaLinks.isEmpty()) {
            log.debug("Skipping DeltaSync. No delta links have been saved");
            return;
        }

        // Call Azure ad and get both DeltaLinks for future calls and also a list of users and their memberships
        LinkedList<BatchedCall> batchJobs = new LinkedList<>(deltaLinks);
        KombitRoleResult result = queryAzureAdWithBatchJobs(batchJobs, false);

        // Save delta links for future use
        this.deltaLinks = result.getDeltaLinks();

        // Convert user groups to fit CoreData API
        CoreDataDeltaJfr coreData = new CoreDataDeltaJfr();
        coreData.setDomain(configuration.getCoreData().getDomain());
        coreData.setEntryList(
                result.getUsers().entrySet()
                        .stream()
                        .map(entry -> new CoreDataDeltaJfrEntry(entry.getKey(), entry.getValue().getAddRoles(), entry.getValue().getRemoveRoles()))
                        .collect(Collectors.toList())
        );

        coreDataService.sendKombitRoleDataDelta(coreData);
    }

    @SuppressWarnings("unchecked")
    private KombitRoleResult queryAzureAdWithBatchJobs(LinkedList<BatchedCall> batchJobs, boolean fullSync) throws Exception {
        // Create result set containing users ids as key and a list of their KOMBIT roles as value
        KombitRoleResult result = new KombitRoleResult();

        // While there are still remaining batch jobs.
        // Either the initial ones OR new NextLink created jobs.
        while (!batchJobs.isEmpty()) {
            // Take at most the first 20 elements (the limit of Microsoft Graph Batch jobs)
            ArrayList<BatchedCall> currentJobs = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                if (batchJobs.peek() != null) {
                    currentJobs.add(batchJobs.pop());
                }
                else {
                    break;
                }
            }

            log.debug(currentJobs.size() + " to be queried in batch job");

            // Construct initial message body
            HashMap<String, Object> body = new HashMap<>();
            body.put("requests", currentJobs);

            // Call Azure AD
            Map<String, Object> batchResponse = postToAzureAd("https://graph.microsoft.com/v1.0/$batch", tokenFetcher.getToken(), body);
            if (!batchResponse.containsKey("responses")) {
                StringBuilder sb = new StringBuilder();
                if (batchResponse.containsKey("error")) {
                    LinkedHashMap<String, Object> error = (LinkedHashMap<String, Object>) batchResponse.get("error");

                    if (error.containsKey("code")) {
                        sb.append(error.get("code")).append(" ");
                    }
                    if (error.containsKey("message")) {
                        sb.append(error.get("message")).append(" ");
                    }
                }
                else {
                    sb.append("Unknown error in batch response");
                }

                if (fullSync) {
                    // If we get an error in full sync we need to stop sync
                    throw new Exception(sb.toString());
                }
                else {
                    // Continue trying to run batch jobs, just not this one.
                    continue;
                }
            }

            // Read and parse Azure AD response
            ArrayList<LinkedHashMap<String, Object>> responses = (ArrayList<LinkedHashMap<String, Object>>) batchResponse.get("responses");
            for (LinkedHashMap<String, Object> resp : responses) {

            	// Error handling
                if (!resp.containsKey("status") || !Objects.equals(200, resp.get("status"))) {
                    // Build error
                    StringBuilder sb = new StringBuilder();
                    sb.append("Request for KOMBIT Role failed ");

                    // Add id
                    sb.append("(").append(resp.getOrDefault("id", "<null>")).append(") ");

                    // Add error message
                    LinkedHashMap<String, Object> error = getObjectFromResponse(resp, "body");
                    if (error != null && error.containsKey("message")) {
                        sb.append("(").append(error.get("message")).append(") ");
                    }

                    log.error("non-200 status from azure: " + sb.toString());
                    continue;
                }

                // Get ID to match Kombit Role
                String id = (String) resp.get("id");
                Jfr kombitGroup = kombitGroups.get(id);
                if (kombitGroup == null) {
                	log.warn("kombit group missing: " + id);
                	continue;
                }

                // Get Body
                LinkedHashMap<String, Object> responseBody = getObjectFromResponse(resp, "body");

                // Check if link is Delta or Next link
                // Create new BatchedCall for next links and push them to the top of the list
                // Remember DeltaLinks for next round of delta sync
                if (responseBody.containsKey("@odata.nextLink")) {
                    // If NextLink, use nextlink for subsequent call
                    String nextLink = (String) responseBody.get("@odata.nextLink");
                    batchJobs.push(new BatchedCall(nextLink.replace("https://graph.microsoft.com/v1.0/", ""), HttpMethod.GET.name(), id));
                }
                else if (responseBody.containsKey("@odata.deltaLink")) {
                    // If DeltaLink, save delta link for future delta update
                    String deltaLink = (String) responseBody.get("@odata.deltaLink");
                    result.getDeltaLinks().add(new BatchedCall(deltaLink.replace("https://graph.microsoft.com/v1.0/", ""), HttpMethod.GET.name(), id));
                }
                else {
                    throw new Exception("No NextLink OR DeltaLink on response. Should NEVER happen");
                }

                // Get list of members per group
                ArrayList<LinkedHashMap<String, Object>> groupList = getListFromResponse(responseBody, "value");

                // In a batch job value is always a list, even though we consistently only return one object here
                if (groupList == null || groupList.size() != 1) {
                    // New link already saved. If list is empty just ignore it here
                    log.debug("Group list was empty: " + kombitGroup.getIdentifier());
                    continue;
                }

                // Fetch Group members
                LinkedHashMap<String, Object> group = groupList.get(0);
                ArrayList<LinkedHashMap<String, Object>> groupMembers = getListFromResponse(group, "members@delta");
                if (groupMembers == null) {
                    // If no group members are supplied, ignore case
                    log.debug("No group members in group: " + kombitGroup.getIdentifier());
                    continue;
                }

                // Iterate over group members and assign KOMBIT roles to each members id in a map
                for (LinkedHashMap<String, Object> groupMember : groupMembers) {
                    // If fullsync, ignore removed members
                    if (!"#microsoft.graph.user".equals(groupMember.get("@odata.type"))) {
                        continue; // We only care about actual users
                    }

                    if (groupMember.containsKey("@removed")) {
                        if (fullSync) {
                            continue; // Full load. Ignore removed members and always ignore non-users
                        }
                        else {
                            // Add KOMBIT role groups to the members list of KOMBIT roles
                            String memberId = (String) groupMember.get("id");
                            if (!result.getUsers().containsKey(memberId)) {
                                result.getUsers().put(memberId, new UserKombitRoleResultEntry());
                            }
                            result.getUsers().get(memberId).getRemoveRoles().add(kombitGroup);
                        }
                    }
                    else {
                        // Add KOMBIT role groups to the members list of KOMBIT roles
                        String memberId = (String) groupMember.get("id");
                        if (!result.getUsers().containsKey(memberId)) {
                            result.getUsers().put(memberId, new UserKombitRoleResultEntry());
                        }

                        result.getUsers().get(memberId).getAddRoles().add(kombitGroup);
                    }
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, Jfr> fetchKombitRoleGroups() throws Exception {

    	// Fetch groupIDs
        if (!StringUtils.hasLength(configuration.getAzureAd().getKombitRolesMainGroupId())) {
            log.error("KombitRolesMainGroupId Not set!");
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("https://graph.microsoft.com/v1.0/groups/").append(configuration.getAzureAd().getKombitRolesMainGroupId()).append("/members");
        sb.append("?$select=id,");
        sb.append(configuration.getAzureAd().getNameField()).append(",");
        sb.append(configuration.getAzureAd().getEntityIdAndCvrField());

        Map<String, Object> response = getFromAzureAd(sb.toString(), tokenFetcher.getToken());
        if (!response.containsKey("value")) {
            throw new Exception("No KOMBIT roles found as members of KombitRolesMainGroupId");
        }
        
        ArrayList<LinkedHashMap<String, Object>> kombitRoles = (ArrayList<LinkedHashMap<String, Object>>) response.get("value");
        HashMap<String, Jfr> kombitGroups = new HashMap<>();
        for (LinkedHashMap<String, Object> role : kombitRoles) {
            // Skip any non-group member of the "KOMBIT Group"
            // We are interested in the group members that are also groups since they each represent a KOMBIT Role
            if (!Objects.equals("#microsoft.graph.group", role.getOrDefault("@odata.type", "<null>"))) {
                continue;
            }

            String id = (String)role.get("id");
            String displayName = (String)role.get(configuration.getAzureAd().getNameField());

            // Configurable values
            String entityID = null;
            String cvr = null;
            if (role.containsKey(configuration.getAzureAd().getEntityIdAndCvrField())) {
                String entityIdAndCvrField = (String)role.get(configuration.getAzureAd().getEntityIdAndCvrField());

                if (entityIdAndCvrField != null && entityIdAndCvrField.contains(":")) {
                	int idx = entityIdAndCvrField.lastIndexOf(":");
                	if (idx < entityIdAndCvrField.length() - 1) {
                    	String entityIDCandidate = entityIdAndCvrField.substring(0, idx);
                    	String cvrCandidate = entityIdAndCvrField.substring(idx + 1);

                		// bit of sanity checking is needed here
                    	if (cvrCandidate.length() == 8 && entityIDCandidate.startsWith("http://")) {
                    		entityID = entityIDCandidate;
                    		cvr = cvrCandidate;
                    	}
                	}
                }
            }

            if (StringUtils.hasLength(entityID) && StringUtils.hasLength(cvr)) {
                kombitGroups.put(id, new Jfr(entityID, cvr));
            }
            else {
                String name = "http://" + configuration.getAzureAd().getKombitRoleUrl() + "/roles/jobrole/" + displayName + "/1";

                kombitGroups.put(id, new Jfr(name, configuration.getAzureAd().getMunicipalCvr()));
            }
        }

        log.info("Fetched " + kombitGroups.size() + " KOMBIT Roles");

        return kombitGroups;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<LinkedHashMap<String, Object>> getListFromResponse(LinkedHashMap<String, Object> obj, String key) {
        if (obj == null || !obj.containsKey(key)) {
            return null;
        }
        return (ArrayList<LinkedHashMap<String, Object>>)obj.get(key);
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<String, Object> getObjectFromResponse(LinkedHashMap<String, Object> obj, String key) {
        if (obj == null || !obj.containsKey(key)) {
            return null;
        }
        return (LinkedHashMap<String, Object>)obj.get(key);
    }


    private Map<String, Object> getFromAzureAd(String url, BearerToken token) throws HttpResponseException, NotAcceptableStatusException, JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.getAccessToken());
        headers.set(Constants.MaxPageSize, "1000");

        HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);

        if (log.isDebugEnabled()) {
            ObjectMapper mapper = new ObjectMapper();
            log.debug("===========================request begin=============================================");
            log.debug("URI         : {}", url);
            log.debug("Headers     : {}", request.getHeaders());
            log.debug("Request body: {}", mapper.writeValueAsString(request.getBody()));
            log.debug("==========================request end================================================");
        }

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<>() { });

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

    private Map<String, Object> postToAzureAd(String url, BearerToken token, Map<String, Object> body) throws HttpResponseException, NotAcceptableStatusException, JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token.getAccessToken());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        if (log.isDebugEnabled()) {
            ObjectMapper mapper = new ObjectMapper();
            log.debug("===========================request begin=============================================");
            log.debug("URI         : {}", url);
            log.debug("Headers     : {}", request.getHeaders());
            log.debug("Request body: {}", mapper.writeValueAsString(request.getBody()));
            log.debug("==========================request end================================================");
        }

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<>() { });

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
}
