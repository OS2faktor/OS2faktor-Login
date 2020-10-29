package dk.digitalidentity.util;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.StatusMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.service.OpenSAMLHelperService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LoggingUtil {
	
	// TODO: refactor this into a utility class that can serialize the payloads

	@Autowired
	private OpenSAMLHelperService openSAMLHelperService;

	public void logAuthnRequest(AuthnRequest authnRequest, String prefix) {
		Objects.requireNonNull(authnRequest, "Could not log AuthnRequest, was null");

		String id = authnRequest.getID();

		Issuer issuer = authnRequest.getIssuer();
		String issuerStr = "";
		if (issuer != null) {
			issuerStr = issuer.getValue();
		}

		DateTime issueInstant = authnRequest.getIssueInstant();
		String instant = "";
		if (issueInstant != null) {
			instant = issueInstant.toString();
		}

		String destination = authnRequest.getDestination();

		log.info(prefix + " AuthnRequest - ID:'" + id + "' Issuer:'" + issuerStr + "' IssueInstant:'" + instant + "' Destination:'" + destination + "'");
	}

	public void logResponse(Response response, String prefix) {
		Objects.requireNonNull(response, "Could not log Response, was null");

		String id = response.getID();
		String destination = response.getDestination();
		String inResponseTo = response.getInResponseTo();

		DateTime issueInstant = response.getIssueInstant();
		String instant = "";
		if (issueInstant != null) {
			instant = issueInstant.toString();
		}

		Issuer issuer = response.getIssuer();
		String issuerStr = "";
		if (issuer != null) {
			issuerStr = issuer.getValue();
		}

		Status status = response.getStatus();
		String statusStr = "";
		if (status != null) {
			StatusCode code = status.getStatusCode();
			if (code != null) {
				statusStr += code.getValue();
			}

			StatusMessage message = status.getStatusMessage();
			if (message != null) {
				statusStr += " " + message.getMessage();
			}
		}

		log.info(prefix + " Response - ID:'" + id + "' InResponseTo:'" + inResponseTo + "' Issuer:'" + issuerStr + "' Status:'" + statusStr + "' IssueInstant:'" + instant + "' Destination:'" + destination + "'");
	}

	public void logAssertion(Assertion assertion, String prefix) {
		if (assertion.getAttributeStatements() != null && assertion.getAttributeStatements().size() > 0) {
			Map<String, String> attributeValues = openSAMLHelperService.extractAttributeValues(assertion.getAttributeStatements().get(0));
			
			StringBuilder builder = new StringBuilder();
			for (String key : attributeValues.keySet()) {
				if (builder.length() > 0) {
					builder.append(", ");
				}
				
				builder.append(key + "=" + attributeValues.get(key));
			}

			log.info(prefix + " Assertion: " + builder.toString());
		}
	}

	public void logLogoutRequest(LogoutRequest logoutRequest, String prefix) {
		Objects.requireNonNull(logoutRequest, "Could not log LogoutRequest, was null");

		String id = logoutRequest.getID();

		DateTime issueInstant = logoutRequest.getIssueInstant();
		String instant = "";
		if (issueInstant != null) {
			instant = issueInstant.toString();
		}

		Issuer issuer = logoutRequest.getIssuer();
		String issuerStr = "";
		if (issuer != null) {
			issuerStr = issuer.getValue();
		}

		String sessionIndexes = logoutRequest.getSessionIndexes().stream().map(sessionIndex -> sessionIndex.getSessionIndex()).collect(Collectors.joining(", ", "[", "]"));
		String destination = logoutRequest.getDestination();

		log.info(prefix + " LogoutRequest - ID:'" + id + "' Issuer:'" + issuerStr + "' IssueInstant:'" + instant + "' SessionIndexes:" + sessionIndexes + "' Destination:'" + destination + "'");
	}

	public void logLogoutResponse(LogoutResponse logoutResponse, String prefix) {
		Objects.requireNonNull(logoutResponse, "Could not log LogoutResponse, was null");

		String id = logoutResponse.getID();

		DateTime issueInstant = logoutResponse.getIssueInstant();
		String instant = "";
		if (issueInstant != null) {
			instant = issueInstant.toString();
		}

		Issuer issuer = logoutResponse.getIssuer();
		String issuerStr = "";
		if (issuer != null) {
			issuerStr = issuer.getValue();
		}

		String inResponseTo = logoutResponse.getInResponseTo();

		Status status = logoutResponse.getStatus();
		String statusStr = "";
		if (status != null) {
			StatusCode code = status.getStatusCode();
			if (code != null) {
				statusStr += code.getValue();
			}

			StatusMessage message = status.getStatusMessage();
			if (message != null) {
				statusStr += " " + message.getMessage();
			}
		}

		String destination = logoutResponse.getDestination();

		log.info(prefix + " LogoutResponse - ID:'" + id + "' InResponseTo:'" + inResponseTo + "' Issuer:'" + issuerStr + "' Status:'" + statusStr + "' IssueInstant:'" + instant + "' Destination:'" + destination + "'");
	}
}
