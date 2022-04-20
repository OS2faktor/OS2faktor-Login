package dk.digitalidentity.mvc.otherUsers.dto;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public class PasswordChangeForm implements Serializable {
	private static final long serialVersionUID = -286544720952319815L;

	private Long personId;
	private String personName;
	private String password;
	private String confirmPassword;

	public PasswordChangeForm(Person person) {
		this.personId = person.getId();
		this.personName = person.getName() + " (" + PersonService.getUsername(person) + ")";
	}
}
