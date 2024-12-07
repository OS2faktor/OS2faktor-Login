package dk.digitalidentity.util;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;

import jakarta.servlet.http.HttpServletRequest;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;

public class HttpRedirectUtil {

	public static MessageContext<SAMLObject> getMessageContext(HttpServletRequest request) throws RequesterException, ResponderException {
		try {
			HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();
			decoder.setHttpServletRequest(request);
			decoder.initialize();
			decoder.decode();
			
			return decoder.getMessageContext();
		}
		catch (ComponentInitializationException e) {
			throw new ResponderException("Kunne ikke initialisere afkoder", e);
		}
		catch (MessageDecodingException e) {
			throw new RequesterException("Kunne ikke afkode besked", e);
		}
	}
}