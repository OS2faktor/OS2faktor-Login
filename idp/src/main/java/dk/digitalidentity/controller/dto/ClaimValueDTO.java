package dk.digitalidentity.controller.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class ClaimValueDTO implements Serializable {
	private static final long serialVersionUID = 7952842897605866722L;
	private String displayName;
	private List<String> acceptedValues;

	public ClaimValueDTO(String displayName, List<String> acceptedValues) {
		this.displayName = displayName;
		this.acceptedValues = acceptedValues;
	}
}
