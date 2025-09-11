package dk.digitalidentity.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.bouncycastle.util.encoders.Base64;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.RoleDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.SqlServiceProviderAdvancedClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderCondition;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderGroupClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderMfaExemptedDomain;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RoleCatalogueOperation;
import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.common.serviceprovider.ServiceProviderConfig;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.CertificateDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ClaimDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ConditionDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.EndpointDTO;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ServiceProviderDTO;
import jakarta.transaction.Transactional;
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
    private List<ServiceProviderConfig> serviceProviderConfigs;

    @Autowired
    private DomainService domainService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    public List<ServiceProviderDTO> getStaticServiceProviderDTOs(boolean fetchMetadata) {
        ArrayList<ServiceProviderDTO> result = new ArrayList<>();

        for (ServiceProviderConfig config : serviceProviderConfigs) {
            if (!config.isEnabled()) {
                continue;
            }

            result.add(getServiceProviderDTO(config, fetchMetadata));
        }

        return result;
    }
    
    public ServiceProviderDTO getStaticServiceProviderDTOByName(String name) {
        if (name == null || serviceProviderConfigs == null || serviceProviderConfigs.size() == 0) {
            return null;
        }

        for (ServiceProviderConfig config : serviceProviderConfigs) {
            if (!config.isEnabled()) {
                continue;
            }

            if (Objects.equals(config.getName(), name)) {
            	return getServiceProviderDTO(config, true);
            }
        }

        return null;
    }

    public ServiceProviderDTO getMetadataDTO(ServiceProviderConfig spConfig, boolean fetchMetadata) {
        switch (spConfig.getProtocol()) {
            case SAML20:
            case WSFED:
            	return getServiceProviderDTO(spConfig, fetchMetadata);
            case OIDC10:
                ServiceProviderDTO serviceProviderDTO = new ServiceProviderDTO(spConfig, null, null);

                RegisteredClient oidcClient = registeredClientRepository.findByClientId(spConfig.getEntityId());
                if (oidcClient == null) {
                    log.warn("Could not find oidc client for spconfig: " + spConfig.getName());
                    return serviceProviderDTO;
                }
                
                serviceProviderDTO.setExistingSecret(StringUtils.hasText(oidcClient.getClientSecret()));
                serviceProviderDTO.setRedirectURLs(new ArrayList<>(oidcClient.getRedirectUris()));
                serviceProviderDTO.setLogoutURLs(new ArrayList<>(oidcClient.getPostLogoutRedirectUris()));
				serviceProviderDTO.setRequirePKCE(oidcClient.getClientSettings().isRequireProofKey());
				serviceProviderDTO.setPublicClient(oidcClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE));

                return serviceProviderDTO;
			default:
                throw new IllegalStateException("Unexpected value: " + spConfig.getProtocol());
        }
    }
    
    // TODO: really should move all these methods into a ServiceProviderService - they do not belong in MetadataService
    @Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)
    public SqlServiceProviderConfiguration saveConfiguration(ServiceProviderDTO serviceProviderDTO) throws Exception {
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
            config.setAdvancedClaims(new HashSet<>());
            config.setGroupClaims(new HashSet<>());
            config.setConditions(new HashSet<>());
            config.setMfaExemptions(new HashSet<>());
            config.setProtocol(Protocol.valueOf(serviceProviderDTO.getProtocol()));
            config.setDelayedMobileLogin(true);
        }

        if (!createScenario) {
            boolean sameProtocol = Objects.equals(config.getProtocol().name(), serviceProviderDTO.getProtocol());
            if (!sameProtocol) {
                throw new IllegalStateException("Cannot change protocol of a ServiceProvider");
            }
        }

        // Update fields
        config.setName(serviceProviderDTO.getName());
		config.setMetadataUrl(StringUtils.hasLength(serviceProviderDTO.getMetadataUrl()) ? serviceProviderDTO.getMetadataUrl().trim() : null);
        config.setMetadataContent(serviceProviderDTO.getMetadataContent());
        config.setNameIdFormat(serviceProviderDTO.getNameIdFormat());
        config.setNameIdValue(serviceProviderDTO.getNameIdValue());
        config.setForceMfaRequired(serviceProviderDTO.getForceMfaRequired());
        config.setPreferNemid(serviceProviderDTO.isPreferNemid());
        config.setPreferNIST(serviceProviderDTO.isPreferNIST());
        config.setRequireOiosaml3Profile(serviceProviderDTO.isRequireOiosaml3Profile());
        config.setNemLogInBrokerEnabled(serviceProviderDTO.isNemLogInBrokerEnabled());
        config.setNsisLevelRequired(serviceProviderDTO.getNsisLevelRequired());
        config.setEncryptAssertions(serviceProviderDTO.isEncryptAssertions());
        config.setEnabled(serviceProviderDTO.isEnabled());
        config.setAllowMitidErvhervLogin(serviceProviderDTO.isAllowMitidErhvervLogin());
        config.setAllowAnonymousUsers(serviceProviderDTO.isAllowAnonymousUsers());
        config.setCertificateAlias(serviceProviderDTO.getCertificate());
        config.setDelayedMobileLogin(serviceProviderDTO.isDelayedMobileLogin());

		if (serviceProviderDTO.getPasswordExpiry() != null && serviceProviderDTO.getMfaExpiry() != null) {
			Long passwordExpiry = serviceProviderDTO.getPasswordExpiry();
			Long mfaExpiry = serviceProviderDTO.getMfaExpiry();

			if (passwordExpiry < mfaExpiry) {
				throw new IllegalStateException("Sessionudløb for kodeord skal være længere eller ens med sessionsudløb for 2-faktor");
			}

			config.setCustomPasswordExpiry(passwordExpiry);
			config.setCustomMfaExpiry(mfaExpiry);
		}
		else {
			config.setCustomPasswordExpiry(null);
			config.setCustomMfaExpiry(null);
		}

        // Sets or updates Static-, Dynamic-, and RoleCatalogue-claims
        setSPClaims(serviceProviderDTO, config);

        // Sets or updates Domain and Group conditions
        setSPConditions(serviceProviderDTO, config);
        
        // Sets or updates exempted mfa domains
        setSPExemptedMfaDomains(serviceProviderDTO, config);

        switch (config.getProtocol()) {
            case SAML20:
                // Fetch metadata, setting the entityId if in createScenario
            	if (createScenario) {
            		updateConfigWithEntityIdFromMetadata(config);
            	}
                break;
            case OIDC10:
                if (createScenario) {
                    config.setEntityId(serviceProviderDTO.getEntityId());
                }
                createOrUpdateRegisteredClientBasedOnSP(serviceProviderDTO, config, createScenario);
                break;
			case WSFED:
				// Fetch metadata, setting the entityId if in createScenario
				if (createScenario) {
					updateConfigWithEntityIdFromMetadata(config);
				}
				break;
			default:
                throw new IllegalStateException("Unexpected value: " + config.getProtocol());
        }

        // Save and return the new metadata.
        return configurationService.save(config);
    }
    
    public void monitorCertificates() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 14);
        Date futereDate = cal.getTime();

        // monitor static ServiceProviders (logging errors, because it is something we need to look at)
        try {
            List<ServiceProviderDTO> serviceProviderDTOs = getStaticServiceProviderDTOs(true);
            for (ServiceProviderDTO serviceProviderDTO : serviceProviderDTOs) {
            	if (!serviceProviderDTO.isEnabled() || serviceProviderDTO.isDoNotMonitorCertificates()) {
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

        // Monitor SQL configured ServiceProviders, logging warn, because it is something the customer needs to look at (but flag it on the SP)
        for (SqlServiceProviderConfiguration sqlSPConfig : configurationService.getAll()) {
        	if (!sqlSPConfig.isEnabled() || !Objects.equals(sqlSPConfig.getProtocol(), Protocol.SAML20)) {
        		continue;
        	}
        	
        	boolean badMetadata = false;

            try {
                EntityDescriptor metadata = getMetadata(sqlSPConfig);
                if (metadata == null) {
                    log.warn("Error in Monitor Certificates task, metadata was null for SQL SP: '" + sqlSPConfig.getName() + "' EntityId, URL or metadata content might be misconfigured?");
                    badMetadata = true;
                }
                else {
	                SPSSODescriptor spssoDescriptor = metadata.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
	                if (spssoDescriptor == null) {
	                	List<RoleDescriptor> descriptors = metadata.getRoleDescriptors();
	                	if (!descriptors.isEmpty()) {
	                		spssoDescriptor = descriptors.stream().filter(SPSSODescriptor.class::isInstance).map(d -> (SPSSODescriptor) d).findAny().orElse(null);
	                	}
	                }
	                
	                if (spssoDescriptor == null) {
	                	log.warn("Error in Monitor Certificates task, no role descriptors in metadata for SQL SP: " + sqlSPConfig.getName());
	                    badMetadata = true;
	                }
	                else {
		                Map<UsageType, List<X509Certificate>> certMap = convertKeyDescriptorsToCert(spssoDescriptor.getKeyDescriptors());
		
		                for (Map.Entry<UsageType, List<X509Certificate>> entry : certMap.entrySet()) {
		                    for (X509Certificate x509Certificate : entry.getValue()) {
		                        if (x509Certificate.getNotAfter().before(futereDate)) {
		                            log.warn(constructCertWarnMessage("SP: " + sqlSPConfig.getName(), entry.getKey().toString(), x509Certificate.getNotAfter()));
		                            badMetadata = true;
		                        }
		                    }
		                }
	                }
                }                
            }
            catch (Exception ex) {
                log.error("Error in Monitor Certificates task while checking SQL configured ServiceProvider (" + sqlSPConfig.getName() + ")", ex);
            }
            
            // update flag
            if (badMetadata != sqlSPConfig.isBadMetadata()) {
            	sqlSPConfig.setBadMetadata(badMetadata);
            	configurationService.save(sqlSPConfig);
            }
        }
    }
    
	public boolean setManualReload(String serviceProviderId) throws Exception {
		try {
			SqlServiceProviderConfiguration spConfig = configurationService.getById(Long.parseLong(serviceProviderId));
			if (spConfig == null) {
				return false;
			}
			
			spConfig.setManualReloadTimestamp(LocalDateTime.now());

			configurationService.save(spConfig);
			return true;
		}
		catch (Exception ex) {
			ServiceProviderDTO staticServiceProvider = getStaticServiceProviderDTOByName(serviceProviderId);

			if (staticServiceProvider == null) {
				return false;
			}

			// TODO: we are not actually forcing a metadata reload - we need to add a similar feature to our static SPs

			return true;
		}
	}
    
    private List<String> getRegisteredEntityIDs() throws Exception {
        List<String> entityIds = CollectionUtils.emptyIfNull(configurationService.getAll()).stream().map(SqlServiceProviderConfiguration::getEntityId).collect(Collectors.toList());

        for (ServiceProviderConfig staticSPConfig : serviceProviderConfigs) {
            entityIds.add(staticSPConfig.getEntityId());
        }

        return entityIds;
    }

	private Element fetchWSFederationMetadata(ServiceProviderConfig sp) throws ParserConfigurationException, SAXException, IOException {
		Element response = null;

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setNamespaceAware(true);
		
		DocumentBuilder builder = factory.newDocumentBuilder();

		String metadataUrl = sp.getMetadataUrl();
		if (StringUtils.hasLength(metadataUrl)) {
			response = builder.parse(URI.create(metadataUrl).toURL().openStream()).getDocumentElement();
			response.normalize();

			return response;
		}
		else {
			response = builder.parse(new ByteArrayInputStream(sp.getMetadataContent().getBytes())).getDocumentElement();
			response.normalize();

			return response;
		}
	}

	private Node getWSFederationRoleDescriptor(Element metadata) {
		NodeList roleDescriptors = metadata.getElementsByTagName("RoleDescriptor");
		if (roleDescriptors.getLength() > 0) {
			Node roleDescriptor = null;
			Node roleDescriptorFallback = null;
			
			for (int i = 0; i < roleDescriptors.getLength(); i++) {
				Node item = roleDescriptors.item(i);
				NamedNodeMap itemAttributes = item.getAttributes();
				if (itemAttributes == null || itemAttributes.getLength() < 1) {
					continue;
				}
	
				Node namedItem = itemAttributes.getNamedItem("xsi:type");
				if (namedItem == null) {
					continue;
				}
	
				if (namedItem.getTextContent().endsWith("SecurityTokenServiceType")) {
					roleDescriptorFallback = item;
				}
				else if (namedItem.getTextContent().endsWith("ApplicationServiceType")) {
					roleDescriptor = item;
				}
			}
	
			if (roleDescriptor != null) {
				return roleDescriptor;
			}
			
			return roleDescriptorFallback;
		}
		
		return null;
	}
	
	private Set<String> getWSFederationEndpoints(Node roleDescriptor) {
		Set<String> allowedEndpoints = new HashSet<>();

		NodeList roleDescriptorChildNodes = roleDescriptor.getChildNodes();
		for (int i = 0; i < roleDescriptorChildNodes.getLength(); i++) {
			Node passiveRequestorEndpoint = roleDescriptorChildNodes.item(i);
			if (!"PassiveRequestorEndpoint".equals(passiveRequestorEndpoint.getLocalName())) {
				continue;
			}

			NodeList passiveRequestorEndpointChildNodes = passiveRequestorEndpoint.getChildNodes();
			for (int j = 0; j < passiveRequestorEndpointChildNodes.getLength(); j++) {
				Node endpointReference = passiveRequestorEndpointChildNodes.item(j);
				if (!"EndpointReference".equals(endpointReference.getLocalName())) {
					continue;
				}

				NodeList endpointReferenceNodes = endpointReference.getChildNodes();
				for (int k = 0; k < endpointReferenceNodes.getLength(); k++) {
					Node address = endpointReferenceNodes.item(k);
					if ("Address".equals(address.getLocalName())) {
						allowedEndpoints.add(address.getTextContent());
					}
				}
			}
		}

		return allowedEndpoints;
	}
	
    private ServiceProviderDTO getServiceProviderDTO(ServiceProviderConfig config, boolean fetchMetadata) {
        ArrayList<EndpointDTO> endpoints = null;
        ArrayList<CertificateDTO> certificates = null;

        if (fetchMetadata) {
        	switch (config.getProtocol()) {
	        	case WSFED: {
	        		try {
	        			Element metadata = fetchWSFederationMetadata(config);
	        			if (metadata != null) {
	        				Node roleDescriptor = getWSFederationRoleDescriptor(metadata);
	        				if (roleDescriptor != null) {
	        					Set<String> allowedEndpoints = getWSFederationEndpoints(roleDescriptor);
	        					if (allowedEndpoints != null && allowedEndpoints.size() > 0) {
	        						endpoints = new ArrayList<>();
	        						for (String allowedEndpoint : allowedEndpoints) {
	        							endpoints.add(new EndpointDTO(allowedEndpoint, "", "PassiveRequestorEndpoint"));
	        						}
	        					}
	        				}
	        			}
	        		}
	        		catch (Exception ex) {
	        			log.warn("Could not fetch Metadata for SP config: " + config.getEntityId(), ex);
	        		}
	        		break;
	        	}
	        	case SAML20: {
	                try {
	                    EntityDescriptor metadata = getMetadata(config);
	
	                    // process existing spSSODescriptor for additional data on view pages
	                    if (metadata != null) {
	                        SPSSODescriptor spssoDescriptor = metadata.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
	
	                        if (spssoDescriptor != null) {
	                            // Process metadata
	                            endpoints = new ArrayList<>(getAssertionConsumerEndpointDTOs(spssoDescriptor));
	                            endpoints.addAll(getLogoutEndpointDTOs(spssoDescriptor));
	
	                            // Process certificates
	                            certificates = getCertificateDTOs(spssoDescriptor);
	                            
	                            if (certificates.size() == 0) {
	                            	log.warn("Unable to find any certificates in metadata for SP config: " + config.getEntityId());                    	                        	
	                            }
	                        }
	                        else {
	                        	log.warn("Unable to find spssoDescriptor in metadata for SP config: " + config.getEntityId());                    	
	                        }
	                    }
	                    else {
	                    	log.warn("Unable to find metadata for SP config: " + config.getEntityId());
	                    }
	                }
	                catch (Exception ex) {
	                    log.warn("Could not fetch Metadata for SP config: " + config.getEntityId(), ex);
	                }
	
	                break;
	        	}
	        	case ENTRAMFA:
	        	case OIDC10:
	        		// no endpoints and certficates for these
	        		break;
	        	}
        }

        return new ServiceProviderDTO(config, certificates, endpoints);
    }

    private EntityDescriptor getMetadata(ServiceProviderConfig config) {
        return getMetadata(config.getName(), config.getMetadataUrl(), config.getMetadataContent());
    }

    private EntityDescriptor getMetadata(String serviceProviderName, String metadataUrl, String metadataContent) {
    	if (StringUtils.hasLength(metadataContent)) {
    		return getEntityDescriptorFromMetadataString(serviceProviderName, metadataContent);
    	}
    	
    	return getEntityDescriptorFromMetadataUrl(serviceProviderName, metadataUrl);
    }

	private ArrayList<CertificateDTO> getCertificateDTOs(SPSSODescriptor spssoDescriptor) throws Exception {
        ArrayList<CertificateDTO> certificates = new ArrayList<>();
        Map<UsageType, List<X509Certificate>> usageTypeListMap = convertKeyDescriptorsToCert(spssoDescriptor.getKeyDescriptors());

        for (Map.Entry<UsageType, List<X509Certificate>> entry : usageTypeListMap.entrySet()) {
            for (X509Certificate x509Certificate : entry.getValue()) {
                certificates.add(new CertificateDTO(entry.getKey(), x509Certificate));
            }
        }
        
        return certificates;
    }

    private List<EndpointDTO> getLogoutEndpointDTOs(SPSSODescriptor spssoDescriptor) {
        return CollectionUtils.emptyIfNull(spssoDescriptor.getSingleLogoutServices())
                .stream()
                .filter(sls -> SAMLConstants.SAML2_POST_BINDING_URI.equals(sls.getBinding()) || SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(sls.getBinding()))
                .map(sls -> new EndpointDTO("Logout", sls))
                .collect(Collectors.toList());
    }

    private List<EndpointDTO> getAssertionConsumerEndpointDTOs(SPSSODescriptor spssoDescriptor) {
        return CollectionUtils.emptyIfNull(spssoDescriptor.getAssertionConsumerServices())
                .stream()
                .filter(acs -> SAMLConstants.SAML2_POST_BINDING_URI.equals(acs.getBinding()))
                .map(acs -> new EndpointDTO("Assertion consumer", acs))
                .collect(Collectors.toList());
    }

    private void createOrUpdateRegisteredClientBasedOnSP(ServiceProviderDTO serviceProviderDTO, SqlServiceProviderConfiguration config, boolean createScenario) {
        RegisteredClient.Builder builder = null;
        if (createScenario) {
            // Create registeredClient with defaults
            builder = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(serviceProviderDTO.getEntityId())
                    .clientName(serviceProviderDTO.getName())
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .scope("openid");
        }
        else {
            // Fetch Registered client
            RegisteredClient registeredClient = registeredClientRepository.findByClientId(config.getEntityId());
            if (registeredClient == null) {
                throw new IllegalStateException("No Registered Client found for ClientId: " + config.getEntityId());
            }

            builder = RegisteredClient.from(registeredClient);
        }

		if (serviceProviderDTO.isPublicClient()) {
			builder.clientAuthenticationMethods(clientAuthenticationMethods -> {
				clientAuthenticationMethods.clear();
				clientAuthenticationMethods.add(ClientAuthenticationMethod.NONE);
			});
			builder.clientSettings(ClientSettings.builder().requireProofKey(true).build());
		}
		else {
			builder.clientAuthenticationMethods(clientAuthenticationMethods -> {
				clientAuthenticationMethods.clear();
				clientAuthenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
				clientAuthenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_POST);
			});

			builder.clientSettings(ClientSettings.builder().requireProofKey(serviceProviderDTO.isRequirePKCE()).build());
		}


        // Update Registered client
        if (StringUtils.hasText(serviceProviderDTO.getClientSecret())) {
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            builder.clientSecret("{bcrypt}" + passwordEncoder.encode(serviceProviderDTO.getClientSecret()));
        }
        
        List<String> redirectURLs = serviceProviderDTO.getRedirectURLs() != null ? serviceProviderDTO.getRedirectURLs() : new ArrayList<>();
        builder.redirectUris((uris) -> {
			uris.clear();
			uris.addAll(redirectURLs);
		});

        List<String> logoutURLs = serviceProviderDTO.getLogoutURLs() != null ? serviceProviderDTO.getLogoutURLs() : new ArrayList<>();
		builder.postLogoutRedirectUris((uris) -> {
			uris.clear();
			uris.addAll(logoutURLs);
		});

        RegisteredClient registeredClient = builder.build();

        registeredClientRepository.save(registeredClient);
    }

    public void attemptToUpdateEntityId(long id) throws Exception {
        SqlServiceProviderConfiguration config = configurationService.getById(id);
        if (config != null) {
        	updateConfigWithEntityIdFromMetadata(config);
        	
        	configurationService.save(config);
        }
    }

    private void updateConfigWithEntityIdFromMetadata(SqlServiceProviderConfiguration config) throws Exception {
        EntityDescriptor metadata = getMetadata(config);
        if (metadata == null) {
        	throw new Exception("Kunne ikke læse metadata");
        }

        List<String> registeredEntityIDs = getRegisteredEntityIDs();

        if (registeredEntityIDs.contains(config.getEntityId())) {
            throw new Exception("Tjenesteudbyder med entityId: '" + config.getEntityId() + "' findes allerede.");
        }
            
        config.setEntityId(metadata.getEntityID());
    }

    private void setSPExemptedMfaDomains(ServiceProviderDTO serviceProviderDTO, SqlServiceProviderConfiguration config) {
    	
    	// add
    	for (long exemptedDomain : serviceProviderDTO.getExemptedDomains()) {
    		Domain domain = domainService.getById(exemptedDomain);
    		if (domain == null) {
    			log.warn("Unable to find domain: " + exemptedDomain);
    			continue;
    		}

    		if (!config.getMfaExemptions().stream().anyMatch(e -> exemptedDomain == e.getDomain().getId())) {
    			SqlServiceProviderMfaExemptedDomain add = new SqlServiceProviderMfaExemptedDomain();
    			add.setConfiguration(config);
    			add.setDomain(domain);

				config.getMfaExemptions().add(add);
    		}
    	}
    	
    	// remove
    	for (Iterator<SqlServiceProviderMfaExemptedDomain> iterator = config.getMfaExemptions().iterator(); iterator.hasNext();) {
			SqlServiceProviderMfaExemptedDomain exemptedDomain = iterator.next();
			
			if (!serviceProviderDTO.getExemptedDomains().stream().anyMatch(d -> exemptedDomain.getDomain().getId() == d)) {
				iterator.remove();
			}
		}
    }
    
    private void setSPConditions(ServiceProviderDTO serviceProviderDTO, SqlServiceProviderConfiguration config) {
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
            }
            else {
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
            }
            else {
                newConditions.add(condition.get());
            }
        }

        Set<SqlServiceProviderCondition> allConditions = config.getConditions();
        allConditions.clear();
        allConditions.addAll(newConditions);
        config.setConditions(allConditions);
    }

    private void setSPClaims(ServiceProviderDTO serviceProviderDTO, SqlServiceProviderConfiguration config) {
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

        Map<Long, SqlServiceProviderAdvancedClaim> advClaimMap = (config.getAdvancedClaims() != null)
                ? config.getAdvancedClaims()
                        .stream()
                        .collect(Collectors.toMap(SqlServiceProviderAdvancedClaim::getId, SqlServiceProviderAdvancedClaim -> SqlServiceProviderAdvancedClaim))
                : new HashMap<>();

        Map<Long, SqlServiceProviderGroupClaim> groupClaimMap = (config.getGroupClaims() != null)
                ? config.getGroupClaims()
                        .stream()
                        .collect(Collectors.toMap(SqlServiceProviderGroupClaim::getId, SqlServiceProviderGroupClaim -> SqlServiceProviderGroupClaim))
                : new HashMap<>();

        // Separate set of claims from DTO
        HashSet<SqlServiceProviderStaticClaim> staticResult = new HashSet<>();
        HashSet<SqlServiceProviderRequiredField> dynamicResult = new HashSet<>();
        HashSet<SqlServiceProviderRoleCatalogueClaim> rcResult = new HashSet<>();
        HashSet<SqlServiceProviderAdvancedClaim> advResult = new HashSet<>();
        HashSet<SqlServiceProviderGroupClaim> groupResult = new HashSet<>();

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
                    requiredField.setSingleValueOnly(claimDTO.isSingleValueOnly());
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
                    if (rcClaim.getExternalOperation() == RoleCatalogueOperation.GET_USER_ROLES || rcClaim.getExternalOperation() == RoleCatalogueOperation.GET_SYSTEM_ROLES) {
                        rcClaim.setSingleValueOnly(claimDTO.isSingleValueOnly());
                    }
                    else {
                        rcClaim.setSingleValueOnly(false);
                    }
                    rcResult.add(rcClaim);
                    break;
                case ADVANCED:
                	SqlServiceProviderAdvancedClaim advClaim = advClaimMap.get(claimDTO.getId());
                    if (advClaim == null) {
                    	advClaim = new SqlServiceProviderAdvancedClaim();
                    	advClaim.setConfiguration(config);
                    }

                    advClaim.setClaimName(claimDTO.getAttribute());
                    advClaim.setClaimValue(claimDTO.getValue());
                    advClaim.setSingleValueOnly(claimDTO.isSingleValueOnly());
                    advResult.add(advClaim);
                    break;
                case GROUP:
                	SqlServiceProviderGroupClaim groupClaim = groupClaimMap.get(claimDTO.getId());
                    if (groupClaim == null) {
                    	groupClaim = new SqlServiceProviderGroupClaim();
                    	groupClaim.setConfiguration(config);
                    }

                    groupClaim.setClaimName(claimDTO.getAttribute());
                    groupClaim.setClaimValue(claimDTO.getValue());
                    groupClaim.setSingleValueOnly(claimDTO.isSingleValueOnly());

                    Group group = groupService.getById(claimDTO.getGroupId());
                    if (group != null) {
                    	groupClaim.setGroup(group);
                    	groupResult.add(groupClaim);
                    }
                    else {
                    	log.warn("Unable to find group with id: " + claimDTO.getGroupId());
                    }

                    break;
            }
        }

        // TODO: this is not the best way to replace a collection... compare and update please

        config.getStaticClaims().clear();
        config.getStaticClaims().addAll(staticResult);

        config.getRequiredFields().clear();
        config.getRequiredFields().addAll(dynamicResult);

        config.getRcClaims().clear();
        config.getRcClaims().addAll(rcResult);
        
        config.getAdvancedClaims().clear();
        config.getAdvancedClaims().addAll(advResult);
        
        config.getGroupClaims().clear();
        config.getGroupClaims().addAll(groupResult);
    }

    private String constructCertWarnMessage(String name, String usageType, Date expiryDate) {
        return "Certificate expiry warning for: " + name + " (UsageType: " + usageType + ", ExpiryDate: " + expiryDate + ")";
    }

    private String constructCertDebugMessage(String name, String usageType, Date expiryDate) {
        return "Certificate expiry was ok for: " + name + " (UsageType: " + usageType + ", ExpiryDate: " + expiryDate + ")";
    }
    
    private EntityDescriptor getEntityDescriptorFromMetadataString(String serviceProviderName, String metadataContent) {
    	DOMMetadataResolver resolver = null;
    	
    	try {
    		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    		documentBuilderFactory.setNamespaceAware(true);
    		documentBuilderFactory.setIgnoringComments(true);
    		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

	    	InputSource is = new InputSource();
	    	is.setCharacterStream(new StringReader(metadataContent));

	    	Document doc = documentBuilder.parse(is);
	    	
	    	resolver = new DOMMetadataResolver(doc.getDocumentElement());
	    	resolver.setParserPool(new BasicParserPool());
	    	resolver.setFailFastInitialization(true);
	    	resolver.setId(resolver.getClass().getCanonicalName());
	        resolver.initialize();

	    	return resolver.iterator().next();
    	}
    	catch (Exception ex) {
    		log.warn("Failed to parse metadata for SP " + serviceProviderName, ex);
    	}
    	finally {
    		try {
    			resolver.destroy();
    		}
    		catch (Exception ignored) {
    			;
    		}
    	}

    	return null;
    }
    
	static class MetadataDownloadResponseHandler implements ResponseHandler<String> {

		@Override
		public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
			InputStream source = response.getEntity().getContent();
			byte[] bytes = source.readAllBytes();
			
			return new String(bytes);
		}		
	}
    
    private EntityDescriptor getEntityDescriptorFromMetadataUrl(String serviceProviderName, String metadataUrl) {
    	try {
			HttpGet get = new HttpGet(metadataUrl);
			String metadataContent = httpClient.execute(get, new MetadataDownloadResponseHandler());

			if (StringUtils.hasLength(metadataContent)) {
				return getEntityDescriptorFromMetadataString(serviceProviderName, metadataContent);
			}

			log.warn("Unable to download metadata from supplied URL: " + metadataUrl);
    	}
    	catch (Exception ex) {
    		log.warn("Failed to download metadata for SP " + serviceProviderName, ex);
    	}
    	
    	return null;
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

	public void deleteServiceProvider(long id) {
		SqlServiceProviderConfiguration config = configurationService.getById(id);
		if (config != null) {
			configurationService.delete(config);
		}
	}
}
