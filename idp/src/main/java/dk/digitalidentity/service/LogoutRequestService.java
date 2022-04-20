package dk.digitalidentity.service;

import java.security.PublicKey;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.joda.time.DateTime;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.SessionIndex;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.validation.LogoutRequestValidationService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;

@Service
public class LogoutRequestService {

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private LogoutRequestValidationService validationService;

	@Autowired
	private CredentialService credentialService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private OS2faktorConfiguration configuration;

	public MessageContext<SAMLObject> getMessageContext(HttpServletRequest request) throws ResponderException, RequesterException {
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

	public LogoutRequest getLogoutRequest(MessageContext<SAMLObject> messageContext) {
		return (LogoutRequest) messageContext.getMessage();
	}

	public void validateLogoutRequest(HttpServletRequest request, MessageContext<SAMLObject> messageContext, EntityDescriptor metadata, PublicKey publicKey)
			throws RequesterException, ResponderException {
		validationService.validate(request, messageContext, metadata, publicKey);
	}

	public MessageContext<SAMLObject> createMessageContextWithLogoutRequest(LogoutRequest logoutRequest, String destination, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		// Create message context
		MessageContext<SAMLObject> messageContext = new MessageContext<>();

		// Create AuthnRequest
		LogoutRequest outgoingLogoutRequest = createLogoutRequest(logoutRequest, destination, serviceProvider);
		messageContext.setMessage(outgoingLogoutRequest);

		// Destination
		SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
		SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);

		SingleSignOnService endpoint = samlHelper.buildSAMLObject(SingleSignOnService.class);
		endpointContext.setEndpoint(endpoint);

		endpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
		endpoint.setLocation(destination);

		// Signing info
		SignatureSigningParameters signatureSigningParameters = new SignatureSigningParameters();
		signatureSigningParameters.setSigningCredential(credentialService.getBasicX509Credential());
		signatureSigningParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
		messageContext.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signatureSigningParameters);

		return messageContext;
	}

	private LogoutRequest createLogoutRequest(LogoutRequest logoutRequest, String destination, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		LogoutRequest outgoingLR = samlHelper.buildSAMLObject(LogoutRequest.class);

		if (logoutRequest != null) {
			outgoingLR.setID(logoutRequest.getID());
		}
		else {
			outgoingLR.setID(UUID.randomUUID().toString());
		}

		outgoingLR.setDestination(destination);
		outgoingLR.setIssueInstant(new DateTime());

		if (logoutRequest != null && StringUtils.hasLength(logoutRequest.getReason())) {
			outgoingLR.setReason(logoutRequest.getReason());
		}
		else if (logoutRequest == null) {
			outgoingLR.setReason(LogoutRequest.USER_REASON);
		}

		// Create Issuer
		Issuer issuer = samlHelper.buildSAMLObject(Issuer.class);
		outgoingLR.setIssuer(issuer);

		issuer.setValue(configuration.getEntityId());

		// Copy NameID
		NameID nameID = samlHelper.buildSAMLObject(NameID.class);
		outgoingLR.setNameID(nameID);

		nameID.setFormat(serviceProvider.getNameIdFormat());
		nameID.setValue(serviceProvider.getNameId(sessionHelper.getPerson()));

		Map<String, String> map = sessionHelper.getServiceProviderSessions().get(serviceProvider.getEntityId());
		if (map != null) {
			SessionIndex sessionIndex = samlHelper.buildSAMLObject(SessionIndex.class);
			outgoingLR.getSessionIndexes().add(sessionIndex);
			sessionIndex.setSessionIndex(map.get(Constants.SESSION_INDEX));
		}

		return outgoingLR;
	}
}
