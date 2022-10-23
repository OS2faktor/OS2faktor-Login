package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdministratorDTO {
	private long id;
	private String userId;
	private String personName;
	private boolean admin;
	private boolean serviceProviderAdmin;
	private boolean userAdmin;
	private boolean supporter;
	private long domainId;
	private boolean registrant;
	private boolean self;

	public AdministratorDTO(Person person, boolean self) {
		this.id = person.getId();
		this.userId = PersonService.getUsername(person);
		this.personName = person.getName();
		this.admin = person.isAdmin();
		this.userAdmin = person.isUserAdmin();
		this.serviceProviderAdmin = person.isServiceProviderAdmin();
		this.supporter = person.isSupporter();

		if (this.supporter) {

			// if the supporter domain is null, it means that all domains are selected = -1
			this.domainId = person.getSupporter().getDomain() != null ? person.getSupporter().getDomain().getId() : -1;
		}

		this.registrant = person.isRegistrant();
		this.self = self;
	}
}
