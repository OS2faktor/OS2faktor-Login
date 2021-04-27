package dk.digitalidentity.common.dao.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.*;

@Audited
@Entity
@Table(name = "supporters")
@Setter
@Getter
@NoArgsConstructor
public class Supporter {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@OneToOne
	@JoinColumn(name = "domain_id")
	private Domain domain;

	@OneToOne
	@JoinColumn(name = "person_id")
	private Person person;

	public Supporter(Domain domain) {
		this.domain = domain;
	}
}
