package dk.digitalidentity.service;

import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.log.ErrorLogDto;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import org.joda.time.DateTime;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.StatusMessage;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Service
public class ErrorResponseService {

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	public void sendResponderError(HttpServletResponse response, AuthnRequest authnRequest, ResponderException e) throws ResponderException {
		try {
			// Make sure AuthnRequest is not null
			Objects.requireNonNull(authnRequest);

			// Fetch information from AuthnRequest required to send error
			String destination = authnRequestHelper.getConsumerEndpoint(authnRequest);
			String inResponseTo = authnRequest.getID();

			// Send error
			sendError(response, destination, inResponseTo, StatusCode.RESPONDER, e, true, true);
		}
		catch (Exception ex) {
			// If anything goes wrong during the process of sending an error to an SP, throw the error here instead.
			log.warn("Failed sending error to SP (usually due to missing AuthnRequest on session). Throwing error here instead.");
			throw e;
		}
	}

	public void sendRequesterError(HttpServletResponse response, AuthnRequest authnRequest, RequesterException e) throws RequesterException {
		try {
			// Make sure AuthnRequest is not null
			Objects.requireNonNull(authnRequest);

			// Fetch information from AuthnRequest required to send error
			String destination = authnRequestHelper.getConsumerEndpoint(authnRequest);
			String inResponseTo = authnRequest.getID();

			// Send error
			sendError(response, destination, inResponseTo, StatusCode.REQUESTER, e, true, true);
		} catch (Exception ex) {
			// If anything goes wrong during the process of sending an error to an SP, throw the error here instead.
			log.warn("Failed sending error to SP (usually due to missing AuthnRequest on session). Throwing error here instead.");
			throw e;
		}
	}

	public void sendError(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e) {
		sendError(response, destination, inResponseTo, statusCode, e, true, false);
	}

	public void sendError(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e, boolean logged, boolean logoutOfSession) {
		if (logged) {
			log.warn("Sending error to " + destination, e);
		}

		// If we have a person on the session AuditLog error, so Municipalities can troubleshoot via SelfService
		Person person = sessionHelper.getPerson();
		if (person != null) {
			ErrorLogDto errorDetail = new ErrorLogDto(e, destination, person, sessionHelper.getPasswordLevel(), sessionHelper.getPasswordLevelTimestamp(), sessionHelper.getMFALevel(), sessionHelper.getMFALevelTimestamp());
			auditLogger.errorSentToSP(person, errorDetail);
		}

		String relayState = sessionHelper.getRelayState();

		if (logoutOfSession) {
			sessionHelper.invalidateSession();
		}
		
		send(response, destination, inResponseTo, statusCode, e, relayState);
	}

	private void send(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e, String relayState) {
		// attempt to clear any residual incoming authnRequest, to avoid strange behaviour on
		// any following actions that might not be related to an authnRequest
		try {
			sessionHelper.setAuthnRequest(null);
		}
		catch (Exception ex) {
			; // ignore
		}

		try {
			MessageContext<SAMLObject> errorMessageContext = createErrorMessageContext(destination, inResponseTo, statusCode, e);

			// Set RelayState
			if (StringUtils.hasLength(relayState)) {
				SAMLBindingSupport.setRelayState(errorMessageContext, relayState);
			}

			HTTPPostEncoder encoder = new HTTPPostEncoder();
			encoder.setHttpServletResponse(response);
			encoder.setMessageContext(errorMessageContext);
			encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

			encoder.initialize();
			encoder.encode();
		}
		catch (Exception ex) {
			log.error("Failed to send error response", ex);
			if (e != null) {
				log.error("Inner exception", e);				
			}
		}
	}

	private MessageContext<SAMLObject> createErrorMessageContext(String destination, String inResponseTo, String statusCode, Exception e) {

		// Create MessageContext
		MessageContext<SAMLObject> messageContext = new MessageContext<>();

		// Create Response
		Response response = samlHelper.buildSAMLObject(Response.class);
		messageContext.setMessage(response);

		response.setDestination(destination);
		response.setInResponseTo(inResponseTo);
		response.setIssueInstant(new DateTime());
		response.setID(new RandomIdentifierGenerationStrategy().generateIdentifier());

		// Create issuer
		Issuer issuer = samlHelper.buildSAMLObject(Issuer.class);
		response.setIssuer(issuer);

		issuer.setValue(configuration.getEntityId());

		// Create status
		Status status = samlHelper.buildSAMLObject(Status.class);
		response.setStatus(status);

		StatusCode code = samlHelper.buildSAMLObject(StatusCode.class);
		status.setStatusCode(code);

		code.setValue(statusCode);

		StatusMessage statusMessage = samlHelper.buildSAMLObject(StatusMessage.class);
		status.setStatusMessage(statusMessage);

		statusMessage.setMessage(e.getMessage());

		// Set destination
		SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
		SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);

		SingleSignOnService endpoint = samlHelper.buildSAMLObject(SingleSignOnService.class);
		endpoint.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
		endpoint.setLocation(destination);

		endpointContext.setEndpoint(endpoint);

		return messageContext;
	}
}
