package dk.digitalidentity.service.model;

import java.util.Objects;
import java.util.UUID;

import dk.digitalidentity.service.HMacUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class Request {
	private String transactionUuid;     // random uuid
	private String command;             // AUTHENTICATE | SET_PASSWORD | VALIDATE_PASSWORD | UNLOCK_ACCOUNT
	private String target;              // sAMAccountName or NULL for AUTHENTICATE messages
	private String payload;             // password to set/validate or NULL for AUTHENTICATE messages
	private String signature;           // keyed hmac of above
	
	// TODO: add a timestamp to the request, so the client can reject old messages (replay of all password changes for instance).
	//       does not need ms accuracy, just enough to avoid reply of old (quite old actually) password change requests
	
	public Request() {
		this.transactionUuid = UUID.randomUUID().toString();
	}
	
	public void sign(String key) throws Exception {
		switch (command) {
			case "AUTHENTICATE":
				this.signature = HMacUtil.hmac(transactionUuid + "." + command, key);
				break;
			case "SET_PASSWORD":
			case "VALIDATE_PASSWORD":
				this.signature = HMacUtil.hmac(transactionUuid + "." + command + "." + target + "." + payload, key);
				break;
			case "UNLOCK_ACCOUNT":
				this.signature = HMacUtil.hmac(transactionUuid + "." + command + "." + target, key);
				break;
			default:
				throw new Exception("Unknown command: " + command);
		}
	}

	public boolean validateEcho(Response message) {
		switch (command) {
			case "AUTHENTICATE":
				if (Objects.equals(command, message.getCommand())) {
					return true;
				}
				break;
			case "SET_PASSWORD":
			case "VALIDATE_PASSWORD":
			case "UNLOCK_ACCOUNT":
				if (Objects.equals(command, message.getCommand()) &&
					Objects.equals(target, message.getTarget())) {
					return true;
				}
				break;
			default:
				log.error("Unknown command: " + command);
		}

		return false;
	}
}
