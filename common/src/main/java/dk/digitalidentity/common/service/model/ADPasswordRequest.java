package dk.digitalidentity.common.service.model;

import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ADPasswordRequest {
	private String cpr;
	private String sAMAccountName;
	private String password;

	public ADPasswordRequest(Person person, String password) {
		this.cpr = person.getCpr();
		this.sAMAccountName = person.getSamaccountName();
		this.password = password;
	}
}
