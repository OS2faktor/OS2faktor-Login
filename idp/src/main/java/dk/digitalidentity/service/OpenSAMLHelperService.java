package dk.digitalidentity.service;

import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.XMLRuntimeException;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.impl.AssertionMarshaller;
import org.opensaml.saml.saml2.core.impl.AuthnRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutResponseMarshaller;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.namespace.QName;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class OpenSAMLHelperService {

	@SuppressWarnings("unchecked")
	public <T> T buildSAMLObject(final Class<T> clazz) {
		T object = null;
		try {
			XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
			QName defaultElementName = (QName) clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
			object = (T) builderFactory.getBuilder(defaultElementName).buildObject(defaultElementName);
		}
		catch (IllegalAccessException | NoSuchFieldException e) {
			throw new IllegalArgumentException("Could not create SAML object");
		}

		return object;
	}
	
	public Map<String, String> extractAttributeValues(AttributeStatement attributeStatement) {
		Map<String, String> result = new HashMap<>();

		for (Attribute attribute : attributeStatement.getAttributes()) {
			String name = attribute.getName();
			String value = extractAttributeValueValue(attribute);

			// never extract CPR
			if ("dk:gov:saml:attribute:CprNumberIdentifier".equals(name)) {
				continue;
			}
			
			if (StringUtils.hasLength(name) && StringUtils.hasLength(value)) {
				result.put(name, value);
			}
		}
		
		return result;
	}

	public String prettyPrint(Assertion assertion) {
		try {
			AssertionMarshaller marshaller = new AssertionMarshaller();
			Element element = marshaller.marshall(assertion);
			return stringifyXmlElement(element);
		}
		catch (Exception ex) {
			log.error("Failed to generate XML string from Assertion", ex);
			return ex.getMessage();
		}
	}

	public String prettyPrint(AuthnRequest authnRequest) {
		try {
			AuthnRequestMarshaller marshaller = new AuthnRequestMarshaller();
			Element element = marshaller.marshall(authnRequest);
			return stringifyXmlElement(element);
		}
		catch (Exception ex) {
			log.error("Failed to generate XML string from AuthnRequest", ex);
			return ex.getMessage();
		}
	}

	public String prettyPrint(LogoutRequest logoutRequest) {
		try {
			LogoutRequestMarshaller marshaller = new LogoutRequestMarshaller();
			Element element = marshaller.marshall(logoutRequest);
			return stringifyXmlElement(element);
		}
		catch (Exception ex) {
			log.error("Failed to generate XML string from LogoutRequest", ex);
			return ex.getMessage();
		}
	}

	public String prettyPrint(LogoutResponse logoutResponse) {
		try {
			LogoutResponseMarshaller marshaller = new LogoutResponseMarshaller();
			Element element = marshaller.marshall(logoutResponse);
			return stringifyXmlElement(element);
		}
		catch (Exception ex) {
			log.error("Failed to generate XML string from LogoutResponse", ex);
			return ex.getMessage();
		}
	}

	private String stringifyXmlElement(Element element) {
		StringWriter writer = new StringWriter();

		DOMImplementation domImpl = element.getOwnerDocument().getImplementation();
		DOMImplementationLS domImplLS = (DOMImplementationLS) domImpl.getFeature("LS", "3.0");

		LSOutput serializerOut = domImplLS.createLSOutput();
		serializerOut.setCharacterStream(writer);

		LSSerializer serializer = domImplLS.createLSSerializer();
		serializer.write(element, serializerOut);

		return writer.toString();
	}

	private String extractAttributeValueValue(Attribute attribute) {
		for (int i = 0; i < attribute.getAttributeValues().size(); i++) {
			if (attribute.getAttributeValues().get(i) instanceof XSString) {
				XSString str = (XSString) attribute.getAttributeValues().get(i);

				if (AttributeValue.DEFAULT_ELEMENT_LOCAL_NAME.equals(str.getElementQName().getLocalPart()) &&
					SAMLConstants.SAML20_NS.equals(str.getElementQName().getNamespaceURI())) {

					return str.getValue();
				}
			}
			else {
				XSAny ep = (XSAny) attribute.getAttributeValues().get(i);
				if (AttributeValue.DEFAULT_ELEMENT_LOCAL_NAME.equals(ep.getElementQName().getLocalPart()) &&
					SAMLConstants.SAML20_NS.equals(ep.getElementQName().getNamespaceURI())) {
					
					if (ep.getUnknownXMLObjects().size() > 0) {
						StringBuilder res = new StringBuilder();

						for (XMLObject obj : ep.getUnknownXMLObjects()) {
							try {
								res.append(SerializeSupport.nodeToString(marshallObject(obj)));
							}
							catch (MarshallingException ex) {
								log.warn("Failed to marshall attribute - ignoring attribute", ex);
							}
						}

						return res.toString();
					}

					return ep.getTextContent();
				}
			}
		}

		return null;
	}
	
    private static Marshaller getMarshaller(XMLObject xmlObject) {
        return getProviderRegistry().getMarshallerFactory().getMarshaller(xmlObject);
    }
    
    private static XMLObjectProviderRegistry getProviderRegistry() {
        XMLObjectProviderRegistry registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
        if (registry == null) {
            throw new XMLRuntimeException("XMLObjectProviderRegistry was not available from the ConfigurationService");
        }

        return registry;
    }

	private static Element marshallObject(XMLObject object) throws MarshallingException {
		if (object.getDOM() == null) {
			Marshaller m = getMarshaller(object);

			if (m == null) {
				throw new IllegalArgumentException("No unmarshaller for " + object);
			}

			return m.marshall(object);
		}
		
		return object.getDOM();
	}
}
