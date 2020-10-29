package dk.digitalidentity.service;

import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
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

@Slf4j
@Service
public class ErrorResponseService {

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private OS2faktorConfiguration configuration;

	public void sendError(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e) {
		sendError(response, destination, inResponseTo, statusCode, e, true);
	}

	public void sendError(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e, boolean logged) {
		if (logged) {
			log.warn("Sending error to " + destination, e);
		}

		send(response, destination, inResponseTo, statusCode, e);
	}

	private void send(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e) {
		try {
			MessageContext<SAMLObject> errorMessageContext = createErrorMessageContext(destination, inResponseTo, statusCode, e);

			HTTPPostEncoder encoder = new HTTPPostEncoder();
			encoder.setHttpServletResponse(response);
			encoder.setMessageContext(errorMessageContext);
			encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

			encoder.initialize();
			encoder.encode();
		}
		catch (Exception ex) {
			log.error("Failed to send error response", ex);
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
