package dk.digitalidentity.controller;

import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.metadata.ContactPerson;
import org.opensaml.saml.saml2.metadata.ContactPersonTypeEnumeration;
import org.opensaml.saml.saml2.metadata.EmailAddress;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.NameIDFormat;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml.saml2.metadata.impl.EntityDescriptorMarshaller;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.CredentialService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@EnableCaching
public class MetadataController {

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private CredentialService credentialService;

	@Autowired
	private OS2faktorConfiguration configuration;

	@CacheEvict(value = { "primaryCert", "secondaryCert", "selfsignedCert", "IdP_Metadata" }, allEntries = true)
	public void evictCache() {
		;
	}

	@ResponseBody
	@Cacheable(value = "primaryCert")
	@GetMapping(value = "/sso/saml/primaryCert", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<StreamingResponseBody> getPrimaryCertificate() throws Exception {		
		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"os2faktor-idp-primary.cer\"") 
			.body(out -> { 
				try {
					BasicX509Credential credential = credentialService.getBasicX509Credential();
					if (credential != null) {
						out.write(credential.getEntityCertificate().getEncoded());						
					}
					else {
						log.error("Could not find primary certificate!");
					}
				}
				catch (Exception ex) {
					log.error("Failed to serialize primary certificate", ex);
				}

				out.close();
			} 
		);
	}
	
	@ResponseBody
	@Cacheable(value = "secondaryCert")
	@GetMapping(value = "/sso/saml/secondaryCert", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<StreamingResponseBody> getSecondaryCertificate() throws Exception {		
		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"os2faktor-idp-secondary.cer\"") 
			.body(out -> { 
				try {
					BasicX509Credential credential = credentialService.getSecondaryBasicX509Credential();
					if (credential != null) {
						out.write(credential.getEntityCertificate().getEncoded());						
					}
					else {
						log.warn("Could not find secondary certificate!");
					}
				}
				catch (Exception ex) {
					log.error("Failed to serialize secondary certificate", ex);
				}

				out.close();
			} 
		);
	}
	
	@ResponseBody
	@Cacheable(value = "selfsignedCert")
	@GetMapping(value = "/sso/saml/selfsignedCert", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<StreamingResponseBody> getSelfsignedCertificate() throws Exception {		
		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"os2faktor-idp-selfsigned.cer\"") 
			.body(out -> { 
				try {
					BasicX509Credential credential = credentialService.getSelfsignedX509Credential();
					if (credential != null) {
						out.write(credential.getEntityCertificate().getEncoded());						
					}
					else {
						log.warn("Could not find selfsigned certificate!");
					}
				}
				catch (Exception ex) {
					log.error("Failed to serialize selfsigned certificate", ex);
				}

				out.close();
			} 
		);
	}
	
	@ResponseBody
	@Cacheable(value = "IdP_Metadata")
	@GetMapping(value = "/sso/saml/metadata", produces = MediaType.APPLICATION_XML_VALUE)
	public String getIdPMetadata(@RequestParam(name = "MitIDErhverv", required = false, defaultValue = "false") boolean mitIDErhverv, @RequestParam(name = "KOMBIT", required = false, defaultValue = "false") boolean kombit, @RequestParam(name = "cert", required = false, defaultValue = "OCES") String cert) throws ResponderException {
		EntityDescriptor entityDescriptor = createEntityDescriptor();

		// Create IdPSSODescriptor
		IDPSSODescriptor idpssoDescriptor = samlHelper.buildSAMLObject(IDPSSODescriptor.class);
		entityDescriptor.getRoleDescriptors().add(idpssoDescriptor);

		idpssoDescriptor.addSupportedProtocol(SAMLConstants.SAML20P_NS);
		idpssoDescriptor.setWantAuthnRequestsSigned(true);

		// Encryption and Signing descriptors
		List<KeyDescriptor> keyDescriptors = idpssoDescriptor.getKeyDescriptors();

		if (kombit) {
			// special case used for KOMBIT, where we can ONLY send one set of certificates. Here we will send the secondary if configured,
			// otherwise we will send the primary. This only works on the manually uploaded metadata endpoint. If we have configured using
			// the URL in KOMBIT adm (only new Context Handler), then we should point to the normal endpoint
			KeyDescriptor secondarySigning = getSecondaryKeyDescriptor(UsageType.SIGNING);
			KeyDescriptor secondaryEncryption = getSecondaryKeyDescriptor(UsageType.ENCRYPTION);
			
			if (secondarySigning != null && secondaryEncryption != null) {
				keyDescriptors.add(secondarySigning);
				keyDescriptors.add(secondaryEncryption);
			}
			else {
				keyDescriptors.add(getKeyDescriptor(UsageType.SIGNING));
				keyDescriptors.add(getKeyDescriptor(UsageType.ENCRYPTION));
			}
		}
		else {
			if (cert != null && !cert.isEmpty() && Objects.equals(cert, KnownCertificateAliases.SELFSIGNED.toString())) {
				keyDescriptors.add(getSelfsignedKeyDescriptor(UsageType.SIGNING));
				keyDescriptors.add(getSelfsignedKeyDescriptor(UsageType.ENCRYPTION));
			}
			else {
				keyDescriptors.add(getKeyDescriptor(UsageType.SIGNING));
				
				// prefer secondary encryption over primary (even though encryption for IdP's is not super important in metadata)
				KeyDescriptor secondaryEncryption = getSecondaryKeyDescriptor(UsageType.ENCRYPTION);
				if (secondaryEncryption != null) {
					keyDescriptors.add(secondaryEncryption);
				}
				else {
					keyDescriptors.add(getKeyDescriptor(UsageType.ENCRYPTION));
				}
				
				KeyDescriptor secondaryKeyDescriptor = getSecondaryKeyDescriptor(UsageType.SIGNING);
				if (secondaryKeyDescriptor != null) {
					keyDescriptors.add(secondaryKeyDescriptor);
				}
			}
		}

		// Create SSO endpoint
		SingleSignOnService singleSignOnMetadata = samlHelper.buildSAMLObject(SingleSignOnService.class);
		idpssoDescriptor.getSingleSignOnServices().add(singleSignOnMetadata);

		singleSignOnMetadata.setBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect");
		singleSignOnMetadata.setLocation(configuration.getBaseUrl() + "/sso/saml/login");

		// Create SSO endpoint for Post
		if (!mitIDErhverv) {
			SingleSignOnService singleSignOnMetadataPost = samlHelper.buildSAMLObject(SingleSignOnService.class);
			idpssoDescriptor.getSingleSignOnServices().add(singleSignOnMetadataPost);

			singleSignOnMetadataPost.setBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
			singleSignOnMetadataPost.setLocation(configuration.getBaseUrl() + "/sso/saml/login");
		}

		// Create SLO endpoint
		SingleLogoutService singleLogoutService = samlHelper.buildSAMLObject(SingleLogoutService.class);
		idpssoDescriptor.getSingleLogoutServices().add(singleLogoutService);

		singleLogoutService.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
		singleLogoutService.setLocation(configuration.getBaseUrl() + "/sso/saml/logout");
		singleLogoutService.setResponseLocation(configuration.getBaseUrl() + "/sso/saml/logout/response");

		// Create SLO endpoint for Post
		SingleLogoutService singleLogoutServicePost = samlHelper.buildSAMLObject(SingleLogoutService.class);
		idpssoDescriptor.getSingleLogoutServices().add(singleLogoutServicePost);

		singleLogoutServicePost.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
		singleLogoutServicePost.setLocation(configuration.getBaseUrl() + "/sso/saml/logout");
		singleLogoutServicePost.setResponseLocation(configuration.getBaseUrl() + "/sso/saml/logout/response");

		NameIDFormat nameIDFormat = samlHelper.buildSAMLObject(NameIDFormat.class);
		nameIDFormat.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent");
		idpssoDescriptor.getNameIDFormats().add(nameIDFormat);
		
		idpssoDescriptor.getAttributes().add(createAttribute("https://data.gov.dk/model/core/specVersion"));
		idpssoDescriptor.getAttributes().add(createAttribute("https://data.gov.dk/model/core/eid/privilegesIntermediate"));
		idpssoDescriptor.getAttributes().add(createAttribute("https://data.gov.dk/concept/core/nsis/loa"));
		idpssoDescriptor.getAttributes().add(createAttribute("https://data.gov.dk/model/core/eid/professional/cvr"));
		idpssoDescriptor.getAttributes().add(createAttribute("https://data.gov.dk/model/core/eid/professional/orgName"));
		
		ContactPerson contactPerson = samlHelper.buildSAMLObject(ContactPerson.class);
		contactPerson.setType(ContactPersonTypeEnumeration.TECHNICAL);
		EmailAddress emailAddress = samlHelper.buildSAMLObject(EmailAddress.class);
		emailAddress.setAddress("kontakt@digital-identity.dk");
		contactPerson.getEmailAddresses().add(emailAddress);
		entityDescriptor.getContactPersons().add(contactPerson);
		
		// Marshall and send EntityDescriptor
		return marshallMetadata(entityDescriptor);
	}

	private Attribute createAttribute(String attributeName) {
		Attribute attribute = samlHelper.buildSAMLObject(Attribute.class);

		attribute.setName(attributeName);
		attribute.setNameFormat(Constants.ATTRIBUTE_VALUE_FORMAT_URI);

		return attribute;
	}

	private String marshallMetadata(EntityDescriptor entityDescriptor) throws ResponderException {
		try {
			EntityDescriptorMarshaller entityDescriptorMarshaller = new EntityDescriptorMarshaller();
			Element element = entityDescriptorMarshaller.marshall(entityDescriptor);

			StringWriter writer = new StringWriter();

			DOMImplementation domImpl = element.getOwnerDocument().getImplementation();
			DOMImplementationLS domImplLS = (DOMImplementationLS) domImpl.getFeature("LS", "3.0");

			LSOutput serializerOut = domImplLS.createLSOutput();
			serializerOut.setCharacterStream(writer);

			LSSerializer serializer = domImplLS.createLSSerializer();
			serializer.write(element, serializerOut);

			return writer.toString();
		}
		catch (MarshallingException e) {
			throw new ResponderException("Kunne ikke omforme metadata", e);
		}
	}

	private EntityDescriptor createEntityDescriptor() {
		EntityDescriptor entityDescriptor = samlHelper.buildSAMLObject(EntityDescriptor.class);
		entityDescriptor.setEntityID(configuration.getEntityId());
		entityDescriptor.setID("_" + UUID.nameUUIDFromBytes(configuration.getEntityId().getBytes()).toString());

		return entityDescriptor;
	}
	
	private KeyDescriptor getSecondaryKeyDescriptor(UsageType usageType) throws ResponderException {
		KeyInfo secondaryCredentials = credentialService.getSecondaryPublicKeyInfo();
		if (secondaryCredentials == null) {
			return null;
		}

		KeyDescriptor keyDescriptor = samlHelper.buildSAMLObject(KeyDescriptor.class);

		keyDescriptor.setUse(usageType);
		keyDescriptor.setKeyInfo(secondaryCredentials);

		return keyDescriptor;
	}

	private KeyDescriptor getSelfsignedKeyDescriptor(UsageType usageType) throws ResponderException {
		KeyInfo selfsignedCredentials = credentialService.getSelfsignedPublicKeyInfo();
		if (selfsignedCredentials == null) {
			return null;
		}

		KeyDescriptor keyDescriptor = samlHelper.buildSAMLObject(KeyDescriptor.class);

		keyDescriptor.setUse(usageType);
		keyDescriptor.setKeyInfo(selfsignedCredentials);

		return keyDescriptor;
	}

	private KeyDescriptor getKeyDescriptor(UsageType usageType) throws ResponderException {
		KeyDescriptor keyDescriptor = samlHelper.buildSAMLObject(KeyDescriptor.class);

		keyDescriptor.setUse(usageType);
		keyDescriptor.setKeyInfo(credentialService.getPublicKeyInfo());

		return keyDescriptor;
	}
}
