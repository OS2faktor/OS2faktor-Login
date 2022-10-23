package dk.digitalidentity.controller;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.CredentialService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.util.ResponderException;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml.saml2.metadata.impl.EntityDescriptorMarshaller;
import org.opensaml.security.credential.UsageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

@RestController
@EnableCaching
public class MetadataController {

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private CredentialService credentialService;

	@Autowired
	private OS2faktorConfiguration configuration;

	@ResponseBody
	@Cacheable(value = "IdP_Metadata")
	@GetMapping(value = "/sso/saml/metadata", produces = MediaType.APPLICATION_XML_VALUE)
	public String getIdPMetadata() throws ResponderException {
		EntityDescriptor entityDescriptor = createEntityDescriptor();

		// Create IdPSSODescriptor
		IDPSSODescriptor idpssoDescriptor = samlHelper.buildSAMLObject(IDPSSODescriptor.class);
		entityDescriptor.getRoleDescriptors().add(idpssoDescriptor);

		idpssoDescriptor.addSupportedProtocol(SAMLConstants.SAML20P_NS);
		idpssoDescriptor.setWantAuthnRequestsSigned(true);

		// Encryption and Signing descriptors
		List<KeyDescriptor> keyDescriptors = idpssoDescriptor.getKeyDescriptors();

		keyDescriptors.add(getKeyDescriptor(UsageType.SIGNING));
		keyDescriptors.add(getKeyDescriptor(UsageType.ENCRYPTION));

		// Create SSO endpoint
		SingleSignOnService singleSignOnMetadata = samlHelper.buildSAMLObject(SingleSignOnService.class);
		idpssoDescriptor.getSingleSignOnServices().add(singleSignOnMetadata);

		singleSignOnMetadata.setBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect");
		singleSignOnMetadata.setLocation(configuration.getBaseUrl() + "/sso/saml/login");

		// Create SLO endpoint
		SingleLogoutService singleLogoutService = samlHelper.buildSAMLObject(SingleLogoutService.class);
		idpssoDescriptor.getSingleLogoutServices().add(singleLogoutService);

		singleLogoutService.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
		singleLogoutService.setLocation(configuration.getBaseUrl() + "/sso/saml/logout");
		singleLogoutService.setResponseLocation(configuration.getBaseUrl() + "/sso/saml/logout/response");

		// Marshall and send EntityDescriptor
		return marshallMetadata(entityDescriptor);
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

	private KeyDescriptor getKeyDescriptor(UsageType usageType) throws ResponderException {
		KeyDescriptor keyDescriptor = samlHelper.buildSAMLObject(KeyDescriptor.class);

		keyDescriptor.setUse(usageType);
		keyDescriptor.setKeyInfo(credentialService.getPublicKeyInfo());

		return keyDescriptor;
	}
}
