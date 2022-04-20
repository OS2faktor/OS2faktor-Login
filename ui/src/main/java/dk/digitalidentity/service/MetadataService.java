package dk.digitalidentity.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.client.HttpClient;
import org.bouncycastle.util.encoders.Base64;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.ResourceBackedMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.SqlServiceProviderCondition;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.RoleCatalogueOperation;
import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.common.serviceprovider.ServiceProviderConfig;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.CertificateDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ClaimDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ConditionDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.EndpointDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ServiceProviderDTO;
import dk.digitalidentity.util.StringResource;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;

@Service
@Slf4j
public class MetadataService {

	@Autowired
	@Qualifier("DISAML_HTTPClient")
	private HttpClient httpClient;

    @Autowired
    private SqlServiceProviderConfigurationService configurationService;

    @Autowired
    private OS2faktorConfiguration configuration;

    @Autowired
    private List<ServiceProviderConfig> serviceProviderConfigs;

    @Autowired
    private DomainService domainService;

    @Autowired
    private GroupService groupService;

    public List<ServiceProviderDTO> getStaticServiceProviderDTOs() throws Exception {
        ArrayList<ServiceProviderDTO> result = new ArrayList<>();

        for (ServiceProviderConfig config : serviceProviderConfigs) {
            if (!config.enabled()) {
                continue;
            }

            ArrayList<EndpointDTO> endpoints = null;
            ArrayList<CertificateDTO> certificates = null;
            try {
                EntityDescriptor metadata = getMetadata(config.getMetadataUrl(), config.getMetadataContent());

                // process existing spSSODescriptor for additional data on view pages
                if (metadata != null) {
                    SPSSODescriptor spssoDescriptor = metadata.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
                    if (spssoDescriptor != null) {
                        // Process metadata
                        endpoints = new ArrayList<>(getAssertionConsumerEndpointDTOs(spssoDescriptor));
                        endpoints.addAll(getLogoutEndpointDTOs(spssoDescriptor));

                        // Process certificates
                        certificates = getCertificateDTOs(spssoDescriptor);
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not fetch Metadata for SP config: " + config.getEntityId(), ex.getMessage());
                log.debug(ex.getMessage(), ex);
            }

            // Adding can fail in case of SPs using file based metadata since it can fail to find the file this is why we need an extra check.
            try {
                result.add(new ServiceProviderDTO(config, certificates, endpoints));
            } catch (Exception ex) {
                log.error("Could not add ServiceProviderDTO for SP: " + config.getEntityId(), ex);
            }
        }
        return result;
    }

    public List<String> getRegisteredEntityIDs() throws Exception {
        List<String> entityIds = CollectionUtils.emptyIfNull(configurationService.getAll()).stream().map(SqlServiceProviderConfiguration::getEntityId).collect(Collectors.toList());

        for (ServiceProviderConfig staticSPConfig : serviceProviderConfigs) {
            entityIds.add(staticSPConfig.getEntityId());
        }

        return entityIds;
    }

    public ServiceProviderDTO getStaticServiceProviderDTOByName(String name) throws Exception {
        if (name == null || serviceProviderConfigs == null || serviceProviderConfigs.size() == 0) {
            return null;
        }

        for (ServiceProviderConfig config : serviceProviderConfigs) {
            if (!config.enabled()) {
                continue;
            }

            if (Objects.equals(config.getName(), name)) {
                ArrayList<EndpointDTO> endpoints = null;
                ArrayList<CertificateDTO> certificates = null;

                try {
                    EntityDescriptor metadata = getMetadata(config.getMetadataUrl(), config.getMetadataContent());
                    // Process existing spSSODescriptor for additional data on view pages
                    if (metadata != null) {
                        SPSSODescriptor spssoDescriptor = metadata.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
                        if (spssoDescriptor != null) {
                            // Process metadata
                            endpoints = new ArrayList<>(getAssertionConsumerEndpointDTOs(spssoDescriptor));
                            endpoints.addAll(getLogoutEndpointDTOs(spssoDescriptor));

                            // Process certificates
                            certificates = getCertificateDTOs(spssoDescriptor);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Could not fetch Metadata for SP config: " + config.getEntityId(), ex.getMessage());
                    log.debug("Stacktrace", ex);
                }
                return new ServiceProviderDTO(config, certificates, endpoints);
            }
        }

        return null;
    }

    public EntityDescriptor getMetadata(SqlServiceProviderConfiguration config) throws Exception {
        return getMetadata(config.getMetadataUrl(), config.getMetadataContent());
    }
    
    public EntityDescriptor getMetadata(String metadataUrl, String metadataContent) throws Exception {
        AbstractReloadingMetadataResolver resolver = getHttpMetadataProvider(metadataUrl, metadataContent);

        try {
        	EntityDescriptor entityDescriptor = resolver.iterator().next();
            resolver.destroy();

            return entityDescriptor;
        } catch (Exception ex) {
            throw new Exception("Kunne ikke hente metadata", ex);
        }
    }

    public ServiceProviderDTO getMetadataDTO(SqlServiceProviderConfiguration spConfig, boolean fetchMetadata) {
        ArrayList<EndpointDTO> endpoints = null;
        ArrayList<CertificateDTO> certificates = null;

        if (fetchMetadata) {
            try {
                EntityDescriptor metadata = getMetadata(spConfig);

                // Process existing spSSODescriptor for additional data on view pages
                if (metadata != null) {
                    SPSSODescriptor spssoDescriptor = metadata.getSPSSODescriptor(SAMLConstants.SAML20P_NS);

                    if (spssoDescriptor != null) {
                        // process metadata
                        endpoints = new ArrayList<>(getAssertionConsumerEndpointDTOs(spssoDescriptor));
                        endpoints.addAll(getLogoutEndpointDTOs(spssoDescriptor));

                        // process certificates
                        certificates = getCertificateDTOs(spssoDescriptor);
                    }
                }
            } 
            catch (Exception ex) {
                log.warn("Could not fetch Metadata for SP config: " + spConfig.getEntityId(), ex);
            }
        }

        return new ServiceProviderDTO(spConfig, certificates, endpoints);
    }

    public ArrayList<CertificateDTO> getCertificateDTOs(SPSSODescriptor spssoDescriptor) throws Exception {
        ArrayList<CertificateDTO> certificates = new ArrayList<>();
        Map<UsageType, List<X509Certificate>> usageTypeListMap = convertKeyDescriptorsToCert(spssoDescriptor.getKeyDescriptors());
        for (Map.Entry<UsageType, List<X509Certificate>> entry : usageTypeListMap.entrySet()) {
            for (X509Certificate x509Certificate : entry.getValue()) {
                certificates.add(new CertificateDTO(entry.getKey(), x509Certificate));
            }
        }
        return certificates;
    }

    public List<EndpointDTO> getLogoutEndpointDTOs(SPSSODescriptor spssoDescriptor) {
        return CollectionUtils.emptyIfNull(spssoDescriptor.getSingleLogoutServices())
                .stream()
                .filter(sls -> SAMLConstants.SAML2_POST_BINDING_URI.equals(sls.getBinding()) || SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(sls.getBinding()))
                .map(sls -> new EndpointDTO("Logout", sls))
                .collect(Collectors.toList());
    }

    public List<EndpointDTO> getAssertionConsumerEndpointDTOs(SPSSODescriptor spssoDescriptor) {
        return CollectionUtils.emptyIfNull(spssoDescriptor.getAssertionConsumerServices())
                .stream()
                .filter(acs -> SAMLConstants.SAML2_POST_BINDING_URI.equals(acs.getBinding()))
                .map(acs -> new EndpointDTO("Assertion consumer", acs))
                .collect(Collectors.toList());
    }

    // TODO: really should move all these methods into a ServiceProviderService - they do not belong in MetadataService
    public SqlServiceProviderConfiguration saveConfiguration(ServiceProviderDTO serviceProviderDTO) throws RuntimeException {
        // Get configuration if it exists
        SqlServiceProviderConfiguration config = configurationService.getById(Long.parseLong(serviceProviderDTO.getId()));

        // Determine if create scenario
        boolean createScenario = false;
        if (config == null) {
            createScenario = true;
            config = new SqlServiceProviderConfiguration();
            config.setStaticClaims(new HashSet<>());
            config.setRequiredFields(new HashSet<>());
            config.setRcClaims(new HashSet<>());
            config.setConditions(new HashSet<>());

        }

        // Update fields

        config.setName(serviceProviderDTO.getName());
        config.setMetadataUrl(serviceProviderDTO.getMetadataUrl());
        config.setMetadataContent(serviceProviderDTO.getMetadataContent());
        config.setNameIdFormat(serviceProviderDTO.getNameIdFormat());
        config.setNameIdValue(serviceProviderDTO.getNameIdValue());
        config.setForceMfaRequired(serviceProviderDTO.getForceMfaRequired());
        config.setPreferNemid(serviceProviderDTO.isPreferNemid());
        config.setNsisLevelRequired(serviceProviderDTO.getNsisLevelRequired());
        config.setEncryptAssertions(serviceProviderDTO.isEncryptAssertions());
        config.setEnabled(serviceProviderDTO.isEnabled());
        config.setProtocol("SAML20");

        Map<Long, SqlServiceProviderStaticClaim> staticClaimMap = (config.getStaticClaims() != null)
        		? config.getStaticClaims()
        				.stream()
        				.collect(Collectors.toMap(SqlServiceProviderStaticClaim::getId, SqlServiceProviderStaticClaim -> SqlServiceProviderStaticClaim))
        		: new HashMap<>();

        Map<Long, SqlServiceProviderRequiredField> requiredFieldMap = (config.getRequiredFields() != null)
        		? config.getRequiredFields()
        				.stream()
        				.collect(Collectors.toMap(SqlServiceProviderRequiredField::getId, SqlServiceProviderRequiredField -> SqlServiceProviderRequiredField))
        		: new HashMap<>();
        
        Map<Long, SqlServiceProviderRoleCatalogueClaim> rcClaimMap = (config.getRcClaims() != null) 
        		? config.getRcClaims()
        				.stream()
        				.collect(Collectors.toMap(SqlServiceProviderRoleCatalogueClaim::getId, SqlServiceProviderRoleCatalogueClaim -> SqlServiceProviderRoleCatalogueClaim))
                : new HashMap<>();

        // Separate set of claims from DTO
        HashSet<SqlServiceProviderStaticClaim> staticResult = new HashSet<>();
        HashSet<SqlServiceProviderRequiredField> dynamicResult = new HashSet<>();
        HashSet<SqlServiceProviderRoleCatalogueClaim> rcResult = new HashSet<>();

        for (ClaimDTO claimDTO : serviceProviderDTO.getClaims()) {
            switch (claimDTO.getType()) {
                case STATIC:
                    SqlServiceProviderStaticClaim staticClaim = staticClaimMap.get(claimDTO.getId());
                    if (staticClaim == null) {
                        staticClaim = new SqlServiceProviderStaticClaim();
                        staticClaim.setConfiguration(config);
                    }
                    staticClaim.setField(claimDTO.getAttribute());
                    staticClaim.setValue(claimDTO.getValue());
                    staticResult.add(staticClaim);
                    break;
                case DYNAMIC:
                    SqlServiceProviderRequiredField requiredField = requiredFieldMap.get(claimDTO.getId());
                    if (requiredField == null) {
                        requiredField = new SqlServiceProviderRequiredField();
                        requiredField.setConfiguration(config);
                    }

                    requiredField.setAttributeName(claimDTO.getAttribute());
                    requiredField.setPersonField(claimDTO.getValue());
                    dynamicResult.add(requiredField);
                    break;
                case ROLE_CATALOGUE:
                	SqlServiceProviderRoleCatalogueClaim rcClaim = rcClaimMap.get(claimDTO.getId());
                    if (rcClaim == null) {
                    	rcClaim = new SqlServiceProviderRoleCatalogueClaim();
                    	rcClaim.setConfiguration(config);
                    }

                    rcClaim.setClaimName(claimDTO.getAttribute());
                    rcClaim.setClaimValue(claimDTO.getValue());
                    rcClaim.setExternalOperation(RoleCatalogueOperation.valueOf(claimDTO.getExternalOperation()));
                    rcClaim.setExternalOperationArgument(claimDTO.getExternalOperationArgument());
                    rcResult.add(rcClaim);
                	break;
            }
        }

        config.getStaticClaims().clear();
        config.getStaticClaims().addAll(staticResult);

        config.getRequiredFields().clear();
        config.getRequiredFields().addAll(dynamicResult);
        
        config.getRcClaims().clear();
        config.getRcClaims().addAll(rcResult);

        // Handle conditions
        Set<SqlServiceProviderCondition> conditions = config.getConditions();
        Set<SqlServiceProviderCondition> newConditions = new HashSet<>();

        Set<SqlServiceProviderCondition> domainConditions = conditions.stream().filter(condition -> SqlServiceProviderConditionType.DOMAIN.equals(condition.getType())).collect(Collectors.toSet());
        List<ConditionDTO> conditionsDomains = serviceProviderDTO.getConditionsDomains();
        for (ConditionDTO conditionsDomain : conditionsDomains) {
            Optional<SqlServiceProviderCondition> condition = domainConditions.stream().filter(domainCondition -> domainCondition.getDomain().getId() == conditionsDomain.getId()).findAny();
            if (condition.isEmpty()) {
                Domain domain = domainService.getById(conditionsDomain.getId());
                if (domain != null) {
                    newConditions.add(new SqlServiceProviderCondition(config, SqlServiceProviderConditionType.DOMAIN, null, domain));
                }
            } else {
                newConditions.add(condition.get());
            }
        }

        Set<SqlServiceProviderCondition> groupConditions = conditions.stream().filter(condition -> SqlServiceProviderConditionType.GROUP.equals(condition.getType())).collect(Collectors.toSet());
        List<ConditionDTO> conditionsGroups = serviceProviderDTO.getConditionsGroups();
        for (ConditionDTO conditionsGroup : conditionsGroups) {
            Optional<SqlServiceProviderCondition> condition = groupConditions.stream().filter(groupCondition -> groupCondition.getGroup().getId() == conditionsGroup.getId()).findAny();
            if (condition.isEmpty()) {
                Group group = groupService.getById(conditionsGroup.getId());
                if (group != null) {
                    newConditions.add(new SqlServiceProviderCondition(config, SqlServiceProviderConditionType.GROUP, group, null));
                }
            } else {
                newConditions.add(condition.get());
            }
        }

        Set<SqlServiceProviderCondition> allConditions = config.getConditions();
        allConditions.clear();
        allConditions.addAll(newConditions);
        config.setConditions(allConditions);

        // Validate that we can fetch metadata with the current config
        EntityDescriptor metadata = null;

        if (config.isEnabled()) {
            try {
                // If create, fetch entityId from metadata before validating
                List<String> registeredEntityIDs = getRegisteredEntityIDs();

                if (createScenario) {
                    EntityDescriptor rawMetadata = getMetadata(config.getMetadataUrl(), config.getMetadataContent());
                    config.setEntityId(rawMetadata.getEntityID());

                    if (registeredEntityIDs.contains(config.getEntityId())) {
                        throw new RuntimeException("Tjenesteudbyder med entityId: '" + config.getEntityId() + "' findes allerede.");
                    }
                }

                metadata = getMetadata(config);
            } catch (Exception ex) {
                throw new RuntimeException(ex.getMessage(), ex);
            }

            if (!Objects.equals(metadata.getEntityID(), config.getEntityId())) {
                throw new RuntimeException("EntityId fra metadata matchede ikke den konfigurede EntityID");
            }
        }

        // Save and return the new metadata.
        return configurationService.save(config);
    }

    @Transactional
    public void monitorCertificates() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 14);
        Date futereDate = cal.getTime();

        // TODO: could just look at the configured certificates instead
        // monitor os2faktor Login IdP
        try {
        	if (configuration.getIdp().getMetadataUrl() != null) {
                EntityDescriptor entityDescriptor = getMetadata(configuration.getIdp().getMetadataUrl(), null);
                if (entityDescriptor == null) {
                    throw new Exception("Could not find IdP entityDescriptor based on: " + configuration.getIdp().getMetadataUrl());
                }

                IDPSSODescriptor idpssoDescriptor = entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);
                if (idpssoDescriptor == null) {
                    throw new Exception("No IDPSSODescriptor was found in the fetched EntityDescriptor for the IdP");
                }

                // run through the list of certificates looking for any expired or soon to be expired certificates
                Map<UsageType, List<X509Certificate>> certMap = convertKeyDescriptorsToCert(idpssoDescriptor.getKeyDescriptors());
                for (Map.Entry<UsageType, List<X509Certificate>> entry : certMap.entrySet()) {
                    for (X509Certificate x509Certificate : entry.getValue()) {
                        if (x509Certificate.getNotAfter().before(futereDate)) {
                            log.error(constructCertWarnMessage("OS2faktor login IdP", entry.getKey().toString(), x509Certificate.getNotAfter()));
                        }
                        else {
                            log.info(constructCertDebugMessage("OS2faktor login IdP", entry.getKey().toString(), x509Certificate.getNotAfter()));
                        }
                    }
                }
        	}
        	else {
        		// TODO: noone in production has actually configured this, I need to go over all of them and update the config for this to work
        		log.warn("No IdP metadata URL configured for monitoring");
        	}
        }
        catch (Exception ex) {
            log.error("Error in Monitor Certificates task while checking IdentityProvider certificates", ex);
        }

        // monitor static ServiceProviders
        try {
            List<ServiceProviderDTO> serviceProviderDTOs = getStaticServiceProviderDTOs();
            for (ServiceProviderDTO serviceProviderDTO : serviceProviderDTOs) {
            	if (!serviceProviderDTO.isEnabled()) {
            		continue;
            	}
            	
                List<CertificateDTO> certificates = serviceProviderDTO.getCertificates();

                if (certificates != null) {
                    for (CertificateDTO certificate : certificates) {
                        if (certificate.getExpiryDateAsDate().before(futereDate)) {
                            log.error(constructCertWarnMessage("SP: " + serviceProviderDTO.getName(), certificate.getUsageType(), certificate.getExpiryDateAsDate()));
                        }
                        else {
                            log.info(constructCertDebugMessage("SP: " + serviceProviderDTO.getName(), certificate.getUsageType(), certificate.getExpiryDateAsDate()));
                        }
                    }
                }
                else {
                    log.error("Error in Monitor Certificates task, could not fetch certificates for: " + serviceProviderDTO.getName());
                }
            }
        }
        catch (Exception ex) {
            log.error("Error in Monitor Certificates task while checking Statically configured ServiceProviders", ex);
        }

        // Monitor SQL configured ServiceProviders
        for (SqlServiceProviderConfiguration sqlSPConfig : configurationService.getAll()) {
        	if (!sqlSPConfig.isEnabled()) {
        		continue;
        	}
        	
            try {
                EntityDescriptor metadata = getMetadata(sqlSPConfig.getMetadataUrl(), sqlSPConfig.getMetadataContent());
                if (metadata == null) {
                    log.error("Error in Monitor Certificates task, metadata was null for SQL SP: '" + sqlSPConfig.getName() + "' EntityId, URL or metadata content might be misconfigured?");
                    continue;
                }
                
                SPSSODescriptor spssoDescriptor = metadata.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
                Map<UsageType, List<X509Certificate>> certMap = convertKeyDescriptorsToCert(spssoDescriptor.getKeyDescriptors());

                for (Map.Entry<UsageType, List<X509Certificate>> entry : certMap.entrySet()) {
                    for (X509Certificate x509Certificate : entry.getValue()) {
                        if (x509Certificate.getNotAfter().before(futereDate)) {
                            log.error(constructCertWarnMessage("SP: " + sqlSPConfig.getName(), entry.getKey().toString(), x509Certificate.getNotAfter()));
                        }
                        else {
                            log.info(constructCertDebugMessage("SP: " + sqlSPConfig.getName(), entry.getKey().toString(), x509Certificate.getNotAfter()));
                        }
                    }
                }
            }
            catch (Exception ex) {
                log.error("Error in Monitor Certificates task while checking SQL configured ServiceProvider (" + sqlSPConfig.getName() + ")", ex);
            }
        }
    }

    private String constructCertWarnMessage(String name, String usageType, Date expiryDate) {
        return "Certificate expiry warning for: " + name + " (UsageType: " + usageType + ", ExpiryDate: " + expiryDate + ")";
    }

    private String constructCertDebugMessage(String name, String usageType, Date expiryDate) {
        return "Certificate expiry was ok for: " + name + " (UsageType: " + usageType + ", ExpiryDate: " + expiryDate + ")";
    }

    private AbstractReloadingMetadataResolver getHttpMetadataProvider(String metadataURL, String metadataContent) throws Exception {
        try {
            if (metadataURL != null && !metadataURL.isEmpty()) {
                return getHttpMetadataProvider(metadataURL);
            }
            else if (metadataContent != null && !metadataContent.isEmpty()) {
                return getResourceBackedMetadataProvider(metadataContent);
            }
            else {
                throw new Exception("Enten metadataURL eller metadataContent skal konfigureres.");
            }
        }
        catch (IOException e) {
            throw new Exception("Kunne ikke oprette MetadataResolver", e);
        }
    }

    private ResourceBackedMetadataResolver getResourceBackedMetadataProvider(String metadataContent) throws Exception {
    	ResourceBackedMetadataResolver resolver = new ResourceBackedMetadataResolver(new Timer(), new StringResource(metadataContent));
		resolver.setId(UUID.randomUUID().toString());
		resolver.setMinRefreshDelay(1000 * 60 * 60 * 3);
		resolver.setMaxRefreshDelay(1000 * 60 * 60 * 3);

		BasicParserPool parserPool = new BasicParserPool();
		parserPool.initialize();

		resolver.setParserPool(parserPool);
		resolver.initialize();
		
		return resolver;
    }
    
	private HTTPMetadataResolver getHttpMetadataProvider(String metadataURL) throws Exception {
		HTTPMetadataResolver resolver = new HTTPMetadataResolver(httpClient, metadataURL);
		resolver.setId(UUID.randomUUID().toString());
		resolver.setMinRefreshDelay(1000 * 60 * 60 * 3);
		resolver.setMaxRefreshDelay(1000 * 60 * 60 * 3);
		
		BasicParserPool parserPool = new BasicParserPool();
		parserPool.initialize();

		resolver.setParserPool(parserPool);
		resolver.initialize();

		return resolver;
	}

    private Map<UsageType, List<X509Certificate>> convertKeyDescriptorsToCert(List<KeyDescriptor> keyDescriptors) throws Exception {
        HashMap<UsageType, List<X509Certificate>> certificates = new HashMap<>();
        if (keyDescriptors == null) {
            return certificates;
        }

        for (KeyDescriptor keyDescriptor : keyDescriptors) {
            KeyInfo keyInfo = keyDescriptor.getKeyInfo();

            if (keyInfo != null) {
                Optional<X509Data> x509Data = keyInfo.getX509Datas().stream().findFirst();

                if (x509Data.isPresent()) {
                    Optional<org.opensaml.xmlsec.signature.X509Certificate> cert = x509Data.get().getX509Certificates().stream().findFirst();

                    if (cert.isPresent()) {
                        // transform opensaml x509 cert --> java x509 cert
                    	org.opensaml.xmlsec.signature.X509Certificate x509Certificate = cert.get();
                        byte[] bytes = Base64.decode(x509Certificate.getValue());
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                        CertificateFactory instance = null;

                        try {
                            instance = CertificateFactory.getInstance("X.509");
                        }
                        catch (CertificateException ex) {
                            throw new Exception("Kunne ikke oprette 'X509 Certificate' læser", ex);
                        }

                        try {
                            X509Certificate certificate = (X509Certificate) instance.generateCertificate(inputStream);

                            List<X509Certificate> list = certificates.getOrDefault(keyDescriptor.getUse(), new ArrayList<>());
                            list.add(certificate);
                            certificates.put(keyDescriptor.getUse(), list);
                        }
                        catch (CertificateException ex) {
                            throw new Exception("Kunne ikke læse X509 Certificate fra Metadata", ex);
                        }
                    }
                }
            }
        }

        return certificates;
    }

	public boolean setManualReload(long serviceProviderId) {
		SqlServiceProviderConfiguration config = configurationService.getById(serviceProviderId);

		if (config != null) {
			config.setManualReloadTimestamp(LocalDateTime.now());

			configurationService.save(config);
			return true;
		}

		return false;
	}
}
