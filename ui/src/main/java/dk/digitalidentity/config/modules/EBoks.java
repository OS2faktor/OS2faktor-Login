package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EBoks {
	private boolean enabled = false;
	private String materialeId;
	private String senderId;
	private String url = "http://remoteprint.digital-identity.dk/";	
}
