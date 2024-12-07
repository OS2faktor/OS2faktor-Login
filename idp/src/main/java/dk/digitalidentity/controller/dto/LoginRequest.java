package dk.digitalidentity.controller.dto;

import java.util.Objects;

import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.controller.wsfederation.dto.WSFedRequestDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
	private Protocol protocol;
	private String serviceProviderId;
	private String returnURL;
	private String destination;
	private boolean forceAuthn;
	private boolean passive;
	private String userAgent;
	private boolean requireBrokering;
	private String wsFedLoginParameters;

	// fetched from session, stored on its own, should change in the future
	private String relayState;

	// protocol specific
	private AuthnRequest authnRequest;
	private OAuth2AuthorizationCodeRequestAuthenticationToken token;
	private EntraPayload entraPayload;

	public LoginRequest(AuthnRequest authnRequest, String userAgent) {
		protocol = Protocol.SAML20;
		serviceProviderId = authnRequest.getIssuer().getValue();
		returnURL = authnRequest.getAssertionConsumerServiceURL();
		destination = authnRequest.getDestination();
		forceAuthn = authnRequest.isForceAuthn();
		passive = authnRequest.isPassive();
		this.userAgent = userAgent;
		this.authnRequest = authnRequest;

		if (authnRequest.getRequestedAuthnContext() != null && authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs() != null) {
			requireBrokering = authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs().stream()
					.anyMatch(i -> Objects.equals(i.getAuthnContextClassRef(), "https://saml.digital-identity.dk/enable_brokering"));
		}
		else {
			requireBrokering = false;
		}		
	}

	public LoginRequest(OAuth2AuthorizationCodeRequestAuthenticationToken token, String userAgent) {
		this.protocol = Protocol.OIDC10;
		this.serviceProviderId = token.getClientId();
		this.returnURL = token.getRedirectUri();
		this.destination = token.getAuthorizationUri();
		this.forceAuthn = false;
		this.passive = false;
		this.requireBrokering = false;
		this.userAgent = userAgent;
		this.token = token;
	}

	public LoginRequest(WSFedRequestDTO loginParameters, String userAgent, String destination) {
		this.protocol = Protocol.WSFED;

		// the service provider can be determined by wtrealm (roughly the same as AppliesTo value used to scope usage of response assertion)
		this.serviceProviderId = loginParameters.getWtrealm();

		this.returnURL = destination;
		this.destination = destination;

		this.forceAuthn = Objects.equals(0,  loginParameters.getWfresh());

		this.passive = false;
		this.userAgent = userAgent;
		this.wsFedLoginParameters = loginParameters.toString();
	}

	public LoginRequest(LoginRequestDTO loginRequestDTO, AuthnRequest authnRequest, String userAgent) {
		this.protocol = loginRequestDTO.getProtocol();
		this.serviceProviderId = loginRequestDTO.getServiceProviderId();
		this.returnURL = loginRequestDTO.getReturnURL();
		this.destination = loginRequestDTO.getDestination();
		this.forceAuthn = loginRequestDTO.isForceAuthn();
		this.passive = loginRequestDTO.isPassive();
		this.token = loginRequestDTO.getToken();
		this.relayState = loginRequestDTO.getRelayState();
		this.userAgent = userAgent;
		this.authnRequest = authnRequest;
		this.entraPayload = loginRequestDTO.getEntraPayload();
	}

	public LoginRequest(EntraPayload payload, String userAgent) {
		this.protocol = Protocol.ENTRAMFA;

		this.entraPayload = payload;
		this.forceAuthn = false;
		this.passive = false;
		this.requireBrokering = false;
		this.userAgent = userAgent;
	}
}
