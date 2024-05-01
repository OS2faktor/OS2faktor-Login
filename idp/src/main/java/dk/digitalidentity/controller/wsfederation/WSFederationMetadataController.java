package dk.digitalidentity.controller.wsfederation;

import java.io.StringWriter;
import java.util.UUID;

import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.impl.KeyDescriptorMarshaller;
import org.opensaml.security.credential.UsageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.CredentialService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.util.ResponderException;

@RestController
public class WSFederationMetadataController {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private CredentialService credentialService;

	@GetMapping(value = "/ws/metadata", produces = MediaType.TEXT_XML_VALUE)
	public String metadata() throws ResponderException, MarshallingException {
		StringBuilder sb = new StringBuilder();

		sb.append("<?xml version=\"1.0\"?>\n");
		sb.append("<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\" ID=\"").append("_").append(UUID.nameUUIDFromBytes(configuration.getEntityId().getBytes()).toString()).append("\" entityID=\"").append(configuration.getEntityId()).append("\">\n");
		sb.append("<RoleDescriptor xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:fed=\"http://docs.oasis-open.org/wsfed/federation/200706\" xsi:type=\"fed:SecurityTokenServiceType\" protocolSupportEnumeration=\"http://docs.oasis-open.org/ws-sx/ws-trust/200512 http://schemas.xmlsoap.org/ws/2005/02/trust http://docs.oasis-open.org/wsfed/federation/200706\" ServiceDisplayName=\"OS2faktor Login\">\n");

		addKeyDescriptor(sb);

		sb
			.append("<fed:TokenTypesOffered>\n")
				.append("<fed:TokenType Uri=\"urn:oasis:names:tc:SAML:2.0:assertion\"/>\n")
			.append("</fed:TokenTypesOffered>\n");


		sb
			.append("<fed:PassiveRequestorEndpoint>\n")
                .append("<EndpointReference xmlns=\"http://www.w3.org/2005/08/addressing\">\n")
                	.append("<Address>").append(configuration.getBaseUrl()).append("/ws/login/").append("</Address>\n")
				.append("</EndpointReference>\n")
			.append("</fed:PassiveRequestorEndpoint>\n");

		sb.append("</RoleDescriptor>\n");

		sb
			.append("<ContactPerson contactType=\"technical\">")
				.append("<EmailAddress>kontakt@digital-identity.dk</EmailAddress>")
			.append("</ContactPerson>");

		sb.append("</EntityDescriptor>\n");

		return sb.toString();
	}

	private void addKeyDescriptor(StringBuilder sb) throws ResponderException, MarshallingException {
		KeyDescriptor keyDescriptor = getKeyDescriptor(UsageType.SIGNING);
		KeyDescriptorMarshaller keyDescriptorMarshaller = new KeyDescriptorMarshaller();
		Element marshalled = keyDescriptorMarshaller.marshall(keyDescriptor);


		StringWriter writer = new StringWriter();

		DOMImplementation domImpl = marshalled.getOwnerDocument().getImplementation();
		DOMImplementationLS domImplLS = (DOMImplementationLS) domImpl.getFeature("LS", "3.0");

		LSOutput serializerOut = domImplLS.createLSOutput();
		serializerOut.setCharacterStream(writer);

		LSSerializer serializer = domImplLS.createLSSerializer();
		serializer.getDomConfig().setParameter("xml-declaration", false);
		serializer.write(marshalled, serializerOut);
		sb.append(writer.toString());
	}

	private KeyDescriptor getKeyDescriptor(UsageType usageType) throws ResponderException {
		KeyDescriptor keyDescriptor = samlHelper.buildSAMLObject(KeyDescriptor.class);

		keyDescriptor.setUse(usageType);
		keyDescriptor.setKeyInfo(credentialService.getPublicKeyInfo());

		return keyDescriptor;
	}
}