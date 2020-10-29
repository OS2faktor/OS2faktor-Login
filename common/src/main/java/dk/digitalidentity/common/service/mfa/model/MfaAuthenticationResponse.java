package dk.digitalidentity.common.service.mfa.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaAuthenticationResponse {
	private String subscriptionKey;
	private String pollingKey;
	private boolean clientNotified;
	private boolean clientAuthenticated;
	private boolean clientRejected;
	private String challenge;
	private String redirectUrl;
}
