package dk.digitalidentity.common.service.dto;

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
	private boolean isDead;
	
	private boolean doesNotExist;
}
