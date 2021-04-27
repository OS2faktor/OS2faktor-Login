package dk.digitalidentity.service.serviceprovider;

import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.service.RoleCatalogueService;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ServiceProviderFactory {
    @Autowired
    private SqlServiceProviderConfigurationService serviceProviderConfigurationService;

    @Autowired
    private List<ServiceProvider> serviceProviders;

    @Autowired
    private HttpClient httpClient;
    
    @Autowired
    private RoleCatalogueService roleCatalogueService;

    @SneakyThrows
    @PostConstruct
    private void initServiceProviderFactory() {
        serviceProviders = serviceProviders.stream()
        		.filter(serviceProvider -> !(serviceProvider instanceof SqlServiceProvider) && serviceProvider.enabled())
        		.collect(Collectors.toList());

        for (SqlServiceProviderConfiguration config : serviceProviderConfigurationService.getAll()) {
            log.debug("Loading SQL SP with entityID: " + config.getEntityId());
            serviceProviders.add(new SqlServiceProvider(config, httpClient, roleCatalogueService));
        }

        log.info(serviceProviders.size() + " ServiceProviders initialized");
    }


    public ServiceProvider getServiceProvider(AuthnRequest authnRequest) throws RequesterException, ResponderException {
        if (authnRequest.getIssuer() != null) {
            return getServiceProvider(authnRequest.getIssuer().getValue());
        }
        throw new RequesterException("Ingen Issuer fundet i AuthnRequest (" + authnRequest.getID() + ") og kunne derfor ikke finde tjenesteudbyderens instillinger");
    }

    public ServiceProvider getServiceProvider(String entityId) throws RequesterException, ResponderException {
        for (ServiceProvider serviceProvider : serviceProviders) {
            if (Objects.equals(serviceProvider.getEntityId(), entityId)) {
                return serviceProvider;
            }
        }
        throw new RequesterException("Kunne ikke finde en tjenesteudbyder der matcher: '" + entityId + "'");
    }

    public List<ServiceProvider> getServiceProviders() {
        return serviceProviders;
    }
}
