package dk.digitalidentity.service;

import java.io.IOException;

import org.joda.time.DateTime;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.StatusMessage;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.log.ErrorLogDto;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.opensaml.CustomHTTPPostEncoder;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletResponse;
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

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	public void sendError(HttpServletResponse response, LoginRequest loginRequest, Exception ex) throws RequesterException, ResponderException {
		sendError(response, loginRequest, ex, true, false);
	}

	public void sendError(HttpServletResponse response, LoginRequest loginRequest, Exception ex, boolean logged, boolean logoutOfSession) throws RequesterException, ResponderException {
		if (loginRequest == null) {
			handleErrorWithInsufficientData(response, ex, logged, logoutOfSession, "LoginRequest was null, cannot send error anywhere");
			return;
		}

		switch (loginRequest.getProtocol()) {
			case SAML20:
				if (loginRequest.getAuthnRequest() == null) {
					handleErrorWithInsufficientData(response, ex, logged, logoutOfSession, "AuthnRequest was null, but protocol was SAML, cannot send error anywhere");
					return;
				}

				AuthnRequest authnRequest = loginRequest.getAuthnRequest();
				String destination = authnRequestHelper.getConsumerEndpoint(authnRequest);
				String inResponseTo = authnRequest.getID();
				String statusCode = StatusCode.RESPONDER;

				if (ex instanceof RequesterException) {
					statusCode = StatusCode.REQUESTER;
				}

				sendError(response, destination, inResponseTo, statusCode, ex, logged, logoutOfSession);
				break;
			case OIDC10:
				if (loginRequest.getToken() == null) {
					handleErrorWithInsufficientData(response, ex, logged, logoutOfSession, "Token was null, but protocol was OIDC10, cannot send error anywhere");
					return;
				}

				try {
					// Check if the exception being thrown is the generic Oauth2Exception containing the specific error codes and message
					if (ex instanceof OAuth2AuthenticationException) {
						OAuth2AuthenticationException oAuth2AuthenticationException = (OAuth2AuthenticationException) ex;
						sendOIDCError(response, loginRequest.getToken(), oAuth2AuthenticationException.getError());
					}
					else {
						sendOIDCError(response, loginRequest.getToken(), new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, ex.getMessage(), null));
					}
				}
				catch (IOException e) {
					throw new IllegalStateException("Kunne ikke sende OIDC fejl besked", e);
				}
				break;
			case WSFED:
				// TODO: figure out how WSFed want Passive Profile to return/handle errors
				// fejl skal muligvis sendes via noget soap fault, men indtil da lader vi vores IdPFlowControllerAdvice håndtere at vise en fejlbesked.
				throw new ResponderException(ex.getMessage(), ex);
			case ENTRAMFA:
				// TODO: better error handling
				handleErrorWithInsufficientData(response, ex, logged, logoutOfSession, "Ikke muligt at gennemføre step-up til EntraID");
				return;
		}
	}

	private void handleErrorWithInsufficientData(HttpServletResponse response, Exception ex, boolean logged, boolean logoutOfSession, String errMsg) throws ResponderException {
		if (logged) {
			log.warn(errMsg, ex);
		}

		if (logoutOfSession) {
			sessionHelper.invalidateSession();
		}

		try {
			response.sendRedirect("/error");
		}
		catch (IOException e) {
			throw new ResponderException("Unable to redirect to error page", e);
		}
	}

	public void sendOIDCError(HttpServletResponse response, OAuth2AuthorizationCodeRequestAuthenticationToken token, OAuth2Error error) throws IOException {
		if (token == null || !StringUtils.hasText(token.getRedirectUri())) {
			response.sendError(HttpStatus.BAD_REQUEST.value(), error.toString());
			return;
		}

		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(token.getRedirectUri()).queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode());
		if (StringUtils.hasText(error.getDescription())) {
			uriBuilder.queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION, error.getDescription());
		}
		if (StringUtils.hasText(error.getUri())) {
			uriBuilder.queryParam(OAuth2ParameterNames.ERROR_URI, error.getUri());
		}
		if (StringUtils.hasText(token.getState())) {
			uriBuilder.queryParam(OAuth2ParameterNames.STATE, token.getState());
		}
		response.sendRedirect(uriBuilder.toUriString());
	}

	public void sendError(HttpServletResponse response, String destination, String inResponseTo, Exception ex) {
		if (ex instanceof RequesterException) {
			sendError(response, destination, inResponseTo, StatusCode.REQUESTER, ex);
		}
		else {
			sendError(response, destination, inResponseTo, StatusCode.RESPONDER, ex);
		}
	}

	public void sendError(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e) {
		sendError(response, destination, inResponseTo, statusCode, e, true, false);
	}

	private void sendError(HttpServletResponse response, String destination, String inResponseTo, String statusCode, Exception e, boolean logged, boolean logoutOfSession) {
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
			sessionHelper.setLoginRequest(null);
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

			CustomHTTPPostEncoder encoder = new CustomHTTPPostEncoder();
			encoder.setHttpServletResponse(response);
			encoder.setMessageContext(errorMessageContext);
			encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

			encoder.initialize();
			encoder.encode();
		}
		catch (Exception ex) {
			log.warn("Failed to send error response", ex);
			if (e != null) {
				log.warn("Inner exception", e);				
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
