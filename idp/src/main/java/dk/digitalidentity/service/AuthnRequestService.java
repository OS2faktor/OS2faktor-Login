package dk.digitalidentity.service;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.messaging.decoder.servlet.BaseHttpServletRequestXMLMessageDecoder;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPPostDecoder;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.springframework.stereotype.Service;

import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;

@Slf4j
@Service
public class AuthnRequestService {

	public MessageContext<SAMLObject> getMessageContext(HttpServletRequest request) throws RequesterException, ResponderException {
		try {
			BaseHttpServletRequestXMLMessageDecoder<SAMLObject> decoder = "POST" .equals(request.getMethod()) ? new HTTPPostDecoder() : new HTTPRedirectDeflateDecoder();

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
		catch (ComponentInitializationException ex) {
			throw new ResponderException("Kunne ikke initialisere afkoder", ex);
		}
		catch (MessageDecodingException ex) {
			String referer = request.getHeader("referer");
			String method = request.getMethod();
			throw new RequesterException("Kunne ikke afkode " + method + " forespørgsel fra: " + referer, ex);
		}

		// SUK, den rammer ikke her, fordi exceptionen spises i BaseHttpServletRequestXmlMessageDecoder.unmarshalMessage() metoden i line 154
		// TODO: remove this once we know why the XMLParserException gets here
		catch (Exception ex) {

			try {
				String authnRequest = "";
				String queryString = request.getQueryString();

				if (queryString != null) {
					String[] encodedParameters = queryString.split("&");

					for (String param : encodedParameters) {
						String[] keyValuePair = param.split("=");

						// Find RedirectUrl if present, otherwise set empty string
						if ("SAMLRequest".equalsIgnoreCase(keyValuePair[0])) {
							authnRequest = keyValuePair[1];
						}
					}
				}
				
				log.error("authnRequest was '" + authnRequest + "'");
			}
			catch (Exception ex2) {
				log.error("Hmmm...", ex2);
			}

			throw new RequesterException("Kunne ikke deserializere authnRequestet - formatet var ugyldigt", ex);
		}
	}

	public AuthnRequest getAuthnRequest(MessageContext<SAMLObject> messageContext) {
		return (AuthnRequest) messageContext.getMessage();
	}

	public boolean requireNemId(AuthnRequest authnRequest) {
		RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
		if (requestedAuthnContext == null) {
			return false;
		}

		List<AuthnContextClassRef> authnContextClassRefs = requestedAuthnContext.getAuthnContextClassRefs();
		if (authnContextClassRefs == null) {
			return false;
		}

		for (AuthnContextClassRef authnContextClassRef : authnContextClassRefs) {
			if ("https://www.digital-identity.dk/require-nemid".equals(authnContextClassRef.getAuthnContextClassRef())) {
				return true;
			}
		}
		
		return false;
	}
}
