package dk.digitalidentity.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntraIdTokenHint {

	@JsonProperty("acr")
	private EntraAcrTokenHint acr;
}
