package dk.digitalidentity.service.serviceprovider;

import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.service.RoleCatalogueService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @PostConstruct
    public void loadServiceProviderFactory() {
        serviceProviders = serviceProviders.stream()
                .filter(serviceProvider -> !(serviceProvider instanceof SqlServiceProvider) && serviceProvider.enabled())
                .collect(Collectors.toList());

        loadSQLServiceProviders();

        log.info(serviceProviders.size() + " ServiceProviders initialized");
    }

    @Transactional
    public void loadSQLServiceProviders() {
        LocalDateTime nextLastReload = LocalDateTime.now();

        for (SqlServiceProviderConfiguration config : serviceProviderConfigurationService.getAllLoadedFully()) {
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
                
                LocalDateTime oldTimestamp = sqlSP.getManualReloadTimestamp();
                LocalDateTime newTimestamp = config.getManualReloadTimestamp();

                // TODO: this check does not appear to work
                boolean refreshMetadata = !Objects.equals(newTimestamp, oldTimestamp);

                if (Objects.equals(config.getEntityId(), sqlSP.getEntityId())) {
                    if (lastReload == null || lastReload.isBefore(config.getLastUpdated())) {
                        log.info("Updating SQL SP with entityID: " + config.getEntityId());

                        sqlSP.setConfig(config);

                        if (refreshMetadata) {
                        	log.info("Force reloading metadata for " + sqlSP.getEntityId());
                        	sqlSP.reloadMetadata();
                        }
                    }

                    foundExisting = true;
                    break;
                }
            }

            // If the SQL SP config was not matched to an existing SP, create a new one and add it to the list
            if (!foundExisting) {
                log.info("Creating SQL SP with entityID: " + config.getEntityId());

                serviceProviders.add(new SqlServiceProvider(config, httpClient, roleCatalogueService));
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

    public ServiceProvider getServiceProvider(String entityId) throws RequesterException, ResponderException {
        for (ServiceProvider serviceProvider : serviceProviders) {

        	if (Objects.equals(serviceProvider.getEntityId(), entityId)) {
                return serviceProvider;
            }
        }

        log.error("Kunne ikke finde en tjenesteudbyder der matcher: '" + entityId + "'");
        throw new RequesterException("Kunne ikke finde en tjenesteudbyder der matcher: '" + entityId + "'");
    }

    public List<ServiceProvider> getServiceProviders() {
        return serviceProviders;
    }
}
