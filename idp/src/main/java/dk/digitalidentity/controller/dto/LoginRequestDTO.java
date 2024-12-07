package dk.digitalidentity.controller.dto;

import java.io.Serializable;

import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

import dk.digitalidentity.common.dao.model.enums.Protocol;
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
	private EntraPayload entraPayload;

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
		this.entraPayload = loginRequest.getEntraPayload();
	}
}
