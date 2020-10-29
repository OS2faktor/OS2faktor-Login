package dk.digitalidentity.util;

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.w3c.dom.Element;

public class XMLUtil {

	public static XMLObject copyXMLObject(XMLObject object, Marshaller marshaller, Unmarshaller unmarshaller) throws ResponderException {
		try {
			Element marshalledObject = marshaller.marshall(object);
			return unmarshaller.unmarshall(marshalledObject);
		}
		catch (MarshallingException | UnmarshallingException e) {
			throw new ResponderException("Kunne ikke kopiere XML", e);
		}
	}
}
