package dk.digitalidentity.common.service.model;

import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ADPasswordRequest {
	private String domain;
	private String userUuid;
	private String userName;
	private String password;

	public ADPasswordRequest(Person person, String password) {
		this.domain = person.getTopLevelDomain().getName();
		this.userUuid = person.getUuid();
		this.userName = person.getSamaccountName();
		this.password = password;
	}

	public ADPasswordRequest(PasswordChangeQueue change, String password) {
		this.domain = change.getDomain();
		this.userUuid = change.getUuid();
		this.userName = change.getSamaccountName();
		this.password = password;
	}
}
