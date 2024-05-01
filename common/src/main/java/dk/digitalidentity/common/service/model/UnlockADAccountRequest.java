package dk.digitalidentity.common.service.model;

import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UnlockADAccountRequest {
	private String domain;
	private String userName;

	public UnlockADAccountRequest(Person person) {
		this.domain = person.getTopLevelDomain().getName();
		this.userName = person.getSamaccountName();
	}
}
