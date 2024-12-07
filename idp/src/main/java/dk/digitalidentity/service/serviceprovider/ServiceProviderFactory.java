package dk.digitalidentity.service.serviceprovider;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.http.client.HttpClient;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.AdvancedRuleService;
import dk.digitalidentity.common.service.RoleCatalogueService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ServiceProviderFactory {
    private LocalDateTime lastReload;

	@Autowired
    private SqlServiceProviderConfigurationService serviceProviderConfigurationService;

    @Autowired
    private List<ServiceProvider> serviceProviders;

    @Autowired
    private HttpClient httpClient;
    
    @Autowired
    private RoleCatalogueService roleCatalogueService;
    
    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private AdvancedRuleService advancedRuleService;
    
    @Autowired
    private AuditLogger auditLogger;
    
    @PostConstruct
    public void loadServiceProviderFactory() {
        serviceProviders = serviceProviders.stream()
                .filter(serviceProvider -> !(serviceProvider instanceof SqlServiceProvider) && serviceProvider.enabled())
                .collect(Collectors.toList());

        // calling this method bypasses the @Transactional annotation, which is okay in this specific case
        loadSQLServiceProviders();

        log.info(serviceProviders.size() + " ServiceProviders initialized");
    }

    @Transactional
    public void loadSQLServiceProviders() {
        LocalDateTime nextLastReload = LocalDateTime.now();

        List<SqlServiceProviderConfiguration> allConfigs = serviceProviderConfigurationService.getAllLoadedFully();
        
        // add or update
        for (SqlServiceProviderConfiguration config : allConfigs) {
            if (!config.isEnabled()) {
                continue;
            }

            boolean foundExisting = false;
            for (ServiceProvider serviceProvider : serviceProviders) {
                if (!(serviceProvider instanceof SqlServiceProvider)) {
                    continue;
                }

                // Check matching EntityId, if matching check if updated since last reload
                SqlServiceProvider sqlSP = (SqlServiceProvider) serviceProvider;
                
                if (Objects.equals(config.getEntityId(), sqlSP.getEntityId())) {
                    LocalDateTime oldTimestamp = sqlSP.getManualReloadTimestamp();
                    LocalDateTime newTimestamp = config.getManualReloadTimestamp();
                    String oldMetadataUrl = sqlSP.getMetadataUrl();
                    String newMetadataUrl = config.getMetadataUrl();
                    
                    boolean refreshMetadata = !Objects.equals(newTimestamp, oldTimestamp) || !Objects.equals(oldMetadataUrl, newMetadataUrl);
                    boolean recreateResolver = !Objects.equals(oldMetadataUrl, newMetadataUrl);
                    
                    if (lastReload == null || lastReload.isBefore(config.getLastUpdated())) {
                        log.info("Updating SQL SP with entityID: " + config.getEntityId());

                        sqlSP.setConfig(config);

                        if (refreshMetadata) {
                        	log.info("Force reloading metadata for " + sqlSP.getEntityId() + " recreate = " + recreateResolver);
                        	sqlSP.reloadMetadata(recreateResolver);
                        }
                    }

                    foundExisting = true;
                    break;
                }
            }

            // If the SQL SP config was not matched to an existing SP, create a new one and add it to the list
            if (!foundExisting) {
                log.info("Creating SQL SP with entityID: " + config.getEntityId());

                serviceProviders.add(new SqlServiceProvider(config, httpClient, roleCatalogueService, advancedRuleService, auditLogger));
            }
        }
        
        // remove removed serviceProviders
        for (Iterator<ServiceProvider> iterator = serviceProviders.iterator(); iterator.hasNext();) {
			ServiceProvider serviceProvider = iterator.next();

            if (!(serviceProvider instanceof SqlServiceProvider)) {
                continue;
            }

            boolean found = false;
            
            for (SqlServiceProviderConfiguration config : allConfigs) {
                if (!config.isEnabled()) {
                    continue;
                }
                
                if (Objects.equals(config.getEntityId(), serviceProvider.getEntityId())) {
                	found = true;
                	break;
                }
            }
            
            if (!found) {
            	log.info("Removing deleted/disabled SQL SP with entityID: " + serviceProvider.getEntityId());
            	iterator.remove();
            }
        }
        
        lastReload = nextLastReload;
    }

    public ServiceProvider getServiceProvider(AuthnRequest authnRequest) throws RequesterException, ResponderException {
        if (authnRequest != null && authnRequest.getIssuer() != null) {
            return getServiceProvider(authnRequest.getIssuer().getValue());
        }
        
        // TODO: this is a common error, we need this to track down the issues and fix them - remove once we are error free :)
		log.warn("session data dump: " + sessionHelper.serializeSessionAsString());

        throw new RequesterException("Ingen Issuer fundet i AuthnRequest (" + (authnRequest != null ? authnRequest.getID() : "null") + ") og kunne derfor ikke finde tjenesteudbyderens instillinger");
    }

    public ServiceProvider getServiceProvider(LoginRequest loginRequest) throws RequesterException, ResponderException {
        switch (loginRequest.getProtocol()) {
            case SAML20:
                AuthnRequest authnRequest = loginRequest.getAuthnRequest();
                if (authnRequest != null && authnRequest.getIssuer() != null) {
                    ServiceProvider serviceProvider = getServiceProvider(authnRequest.getIssuer().getValue());

                    return serviceProvider;
                }

                // TODO: this is a common error, we need this to track down the issues and fix them - remove once we are error free :)
                log.warn("session data dump: " + sessionHelper.serializeSessionAsString());
                throw new RequesterException("Ingen Issuer fundet i AuthnRequest (" + (authnRequest != null ? authnRequest.getID() : "null") + ") og kunne derfor ikke finde tjenesteudbyderens instillinger");
            case OIDC10:
                OAuth2AuthorizationCodeRequestAuthenticationToken token = loginRequest.getToken();
                if (token != null && StringUtils.hasLength(token.getClientId())) {
                    return getServiceProvider(token.getClientId());
                }

                throw new RequesterException("Ingen ClientId fundet i OAuth2AuthorizationCodeRequestAuthenticationToken og kunne derfor ikke finde tjenesteudbyderens indstillinger");
            case WSFED:
                return getServiceProvider(loginRequest.getServiceProviderId());
            case ENTRAMFA:
            	return getEntraMfaServiceProvider();
        }
        
        throw new IllegalStateException("Unexpected value: " + loginRequest.getProtocol());
    }

	public ServiceProvider getEntraMfaServiceProvider() throws RequesterException, ResponderException {
        for (ServiceProvider serviceProvider : serviceProviders) {
        	if (serviceProvider instanceof EntraMfaServiceProvider) {
        		return serviceProvider;
        	}
        }

        log.warn("Kunne ikke finde en tjenesteudbyder for EntraID MFA");

        throw new RequesterException("Kunne ikke finde en tjenesteudbyder for EntraID MFA");
    }
	
	public ServiceProvider getServiceProvider(String entityId) throws RequesterException, ResponderException {
        for (ServiceProvider serviceProvider : serviceProviders) {

        	for (String spEntityId : serviceProvider.getEntityIds()) {
	        	if (Objects.equals(spEntityId, entityId)) {
	                return serviceProvider;
	            }
        	}
        }

        log.warn("Kunne ikke finde en tjenesteudbyder der matcher: '" + entityId + "'");
        throw new RequesterException("Kunne ikke finde en tjenesteudbyder der matcher: '" + entityId + "'");
    }

    public List<ServiceProvider> getServiceProviders() {
        return serviceProviders;
    }
}
