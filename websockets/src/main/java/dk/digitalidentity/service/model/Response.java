package dk.digitalidentity.service.model;

import java.util.Objects;

import dk.digitalidentity.service.HMacUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@ToString
public class Response {
	private String transactionUuid;   // echo transactionUuid from request
	private String command;           // echo command from request
	private String target;            // echo target from request (except for AUTHENTICATE, where it contains the domain)
	private String status;            // true on success, false otherwise
	private String message;           // optional message (not under signature - status is the field to check against, message is for debugging)
	private String signature;         // keyed hmac on above

	private String serverName;        // optional field - not under signature, just for logging
	private String clientVersion;     // optional field - not under signature, just for logging

	public boolean verify(String key) {
		switch (command) {
			case "AUTHENTICATE":
			case "IS_ALIVE":
			case "SET_PASSWORD":
			case "SET_PASSWORD_WITH_FORCED_CHANGE":
			case "VALIDATE_PASSWORD":
			case "UNLOCK_ACCOUNT":
			case "PASSWORD_EXPIRES_SOON":
				try {
					return Objects.equals(this.signature, HMacUtil.hmac(transactionUuid + "." + command + "." + target + "." + status, key));
				}
				catch (Exception ex) {
					log.error("Failed to verify signature", ex);

					return false;
				}
			default:
				log.error("Unknown command: " + command);
				break;
		}
		
		return false;
	}
}
