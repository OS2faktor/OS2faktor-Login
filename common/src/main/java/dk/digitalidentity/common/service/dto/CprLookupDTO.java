package dk.digitalidentity.common.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CprLookupDTO {
	private String firstname;
	private String lastname;
	private String street;
	private String localname;
	private String postalCode;
	private String city;
	private String country;
	private boolean addressProtected;
	@JsonProperty(value = "isDead")
	private boolean dead;
	private boolean disenfranchised;
	private boolean doesNotExist;
	private List<ChildDTO> children;
}
