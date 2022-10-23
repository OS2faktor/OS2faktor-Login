package dk.digitalidentity.controller.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ClaimValueDTO {
	private String displayName;
	private List<String> acceptedValues;

	public ClaimValueDTO(String displayName, List<String> acceptedValues) {
		this.displayName = displayName;
		this.acceptedValues = acceptedValues;
	}
}
