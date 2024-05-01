package dk.digitalidentity.nemlogin.service.model;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateProfileRequest {
	private String registeredIAL = "Substantial";
	private String targetIal = "Substantial";
	private String givenName;
	private String surname;
	private String cprNumber;
	private String emailAddress;
	
	public UpdateProfileRequest(Person person, String defaultEmail) {
		String name = person.getName();

		int idx = name.lastIndexOf(" ");
		if (idx > 0 && idx < name.length()) {
			givenName = name.substring(0, idx);
			surname = name.substring(idx + 1);
		}
		else {
			givenName = name;
			surname = "ukendt";
		}
		
		cprNumber = person.getCpr();
		emailAddress = person.getEmail();
		
		if (!StringUtils.hasLength(emailAddress)) {
			emailAddress = defaultEmail;
		}
	}
}
