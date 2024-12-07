package dk.digitalidentity.common.dao.model;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
