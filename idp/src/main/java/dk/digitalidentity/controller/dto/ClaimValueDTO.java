package dk.digitalidentity.controller.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ClaimValueDTO implements Serializable {
	private static final long serialVersionUID = 7952842897605866722L;
	private String claimDisplayName;
	private List<String> acceptedValues;
	private Map<String, String> acceptedValuesWithNames;
	private boolean hasNames = false;

	public ClaimValueDTO(String displayName, List<String> acceptedValues) {
		this.claimDisplayName = displayName;
		this.acceptedValues = acceptedValues;
		this.hasNames = false;
	}

	public ClaimValueDTO(String displayName, Map<String, String> acceptedValuesWithNames) {
        this.claimDisplayName = displayName;
        this.acceptedValuesWithNames = acceptedValuesWithNames;
        this.hasNames = true;
    }
}
