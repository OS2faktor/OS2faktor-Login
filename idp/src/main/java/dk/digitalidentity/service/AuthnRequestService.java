package dk.digitalidentity.service;

import dk.digitalidentity.util.ResponderException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.springframework.stereotype.Service;

import dk.digitalidentity.util.RequesterException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;

@Service
public class AuthnRequestService {

	public MessageContext<SAMLObject> getMessageContext(HttpServletRequest request) throws RequesterException, ResponderException {
		try {
			HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();
			decoder.setHttpServletRequest(request);

			BasicParserPool parserPool = new BasicParserPool();
			parserPool.initialize();

			decoder.setParserPool(parserPool);
			decoder.initialize();
			decoder.decode();

			MessageContext<SAMLObject> msgContext = decoder.getMessageContext();
			decoder.destroy();

			return msgContext;
		}
		catch (ComponentInitializationException e) {
			throw new ResponderException("Kunne ikke initialisere afkoder", e);
		}
		catch (MessageDecodingException e) {
			throw new RequesterException("Kunne ikke afkode forespørgsel", e);
		}
	}

	public AuthnRequest getAuthnRequest(MessageContext<SAMLObject> messageContext) {
		return (AuthnRequest) messageContext.getMessage();
	}

	public boolean requireNemId(AuthnRequest authnRequest) {
		RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
		if (requestedAuthnContext == null) { return false; }

		List<AuthnContextClassRef> authnContextClassRefs = requestedAuthnContext.getAuthnContextClassRefs();
		if (authnContextClassRefs == null) { return false; }

		for (AuthnContextClassRef authnContextClassRef : authnContextClassRefs) {
			if ("https://www.digital-identity.dk/require-nemid".equals(authnContextClassRef.getAuthnContextClassRef())) {
				return true;
			}
		}

		return false;
	}
}
