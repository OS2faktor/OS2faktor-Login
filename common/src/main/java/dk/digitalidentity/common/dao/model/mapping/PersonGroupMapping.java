package dk.digitalidentity.common.dao.model.mapping;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.Person;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "persons_groups")
@Getter
@Setter
@NoArgsConstructor
public class PersonGroupMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	@NotNull
	private Person person;

	@JsonBackReference
	@OneToOne
	@JoinColumn(name = "group_id")
	@NotNull
	private Group group;

	public PersonGroupMapping(Person person, Group group) {
		this.person = person;
		this.group = group;
	}

	public void loadFully() {
		if (getPerson() != null) {
			getPerson().getDomain().getName();
			getPerson().getDomain().getChildDomains().size();

			getPerson().getGroups().size();
			getPerson().getTopLevelDomain().getName();
			getPerson().getKombitJfrs().size();
		}

		if (getGroup() != null) {
			getGroup().getMemberMapping().size();
			getGroup().getDomain().getName();
		}
	}
}
