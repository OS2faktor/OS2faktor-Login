package dk.digitalidentity.controller.dto;

import dk.digitalidentity.common.dao.model.enums.Protocol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

import java.io.Serializable;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequestDTO implements Serializable {
	private static final long serialVersionUID = -7535364259720238338L;
	private Protocol protocol;
	private String serviceProviderId;
	private String returnURL;
	private String destination;
	private boolean forceAuthn;
	private boolean passive;
	private String relayState;
	private OAuth2AuthorizationCodeRequestAuthenticationToken token;
	private String wsFedloginParameters;

	public LoginRequestDTO(AuthnRequest authnRequest) {
		protocol = Protocol.SAML20;
		serviceProviderId = authnRequest.getIssuer().getValue();
		returnURL = authnRequest.getAssertionConsumerServiceURL();
		destination = authnRequest.getDestination();
		forceAuthn = authnRequest.isForceAuthn();
		passive = authnRequest.isPassive();
	}

	public LoginRequestDTO(OAuth2AuthorizationCodeRequestAuthenticationToken token) {
		protocol = Protocol.OIDC10;
		serviceProviderId = token.getClientId();
		returnURL = token.getRedirectUri();
		destination = token.getAuthorizationUri();
		forceAuthn = false;
		passive = false;
		this.token = token;
	}

	public LoginRequestDTO(LoginRequest loginRequest) {
		this.protocol = loginRequest.getProtocol();
		this.serviceProviderId = loginRequest.getServiceProviderId();
		this.returnURL = loginRequest.getReturnURL();
		this.destination = loginRequest.getDestination();
		this.forceAuthn = loginRequest.isForceAuthn();
		this.passive = loginRequest.isPassive();
		this.token = loginRequest.getToken();
		this.relayState = loginRequest.getRelayState();
		this.wsFedloginParameters = loginRequest.getWsFedLoginParameters();
	}
}
