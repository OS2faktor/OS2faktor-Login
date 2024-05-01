package dk.digitalidentity.common.service.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ADPasswordResponse {
	public enum ADPasswordStatus { NOOP, OK, FAILURE, TECHNICAL_ERROR, TIMEOUT, INSUFFICIENT_PERMISSION }
	
	private ADPasswordStatus status;
	private String message;
	
	public static boolean isCritical(ADPasswordStatus status) {
		switch (status) {
			case FAILURE:
			case TECHNICAL_ERROR:
			case INSUFFICIENT_PERMISSION:
				return true;
			case NOOP:
			case OK:
			case TIMEOUT:
				return false;
		}
		
		// hmmm.... well, the above code will warn about new possible values we need to handle
		return true;
	}
}
