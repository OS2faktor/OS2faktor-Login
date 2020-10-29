package dk.digitalidentity.service;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
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
import org.opensaml.saml.saml2.core.impl.AssertionMarshaller;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

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
			
			if (!StringUtils.isEmpty(name) && !StringUtils.isEmpty(value)) {
				result.put(name, value);
			}
		}
		
		return result;
	}

	public String prettyPrint(Assertion assertion) {
		try {
			AssertionMarshaller assertionMarshaller = new AssertionMarshaller();
			Element element = assertionMarshaller.marshall(assertion);
			Source source = new DOMSource(element);

			TransformerFactory transFactory = TransformerFactory.newInstance();
			Transformer transformer = transFactory.newTransformer();

			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

			StringWriter buffer = new StringWriter();
			transformer.transform(source, new StreamResult(buffer));
			return buffer.toString();
		}
		catch (Exception ex) {
			log.error("Failed to generate XML string from Assertion", ex);
			
			return ex.getMessage();
		}
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
