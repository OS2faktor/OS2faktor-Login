package dk.digitalidentity.common.dao.model.mapping;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonBackReference;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Audited
@Entity(name = "persons_groups")
@Getter
@Setter
@NoArgsConstructor
public class PersonGroupMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@BatchSize(size = 100)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	@NotNull
	private Person person;

	@JsonBackReference
	@BatchSize(size = 100)
	@OneToOne(fetch = FetchType.LAZY)
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
