package dk.digitalidentity.service.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PidAndCprOrError {
	private String pid;
	private String cpr;
	private String error;
	
	public PidAndCprOrError(String pid, String cpr) {
		this.pid = pid;
		this.cpr = cpr;
	}

	public PidAndCprOrError(String error) {
		this.error = error;
	}

	public boolean hasError() {
		return error != null;
	}
}
