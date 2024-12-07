package dk.digitalidentity.common.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "persons_attributes_set")
@Getter
@Setter
@NoArgsConstructor
public class PersonAttribute {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private String name;

	@Column
	private String displayName;

	public PersonAttribute(String name) {
		this.name = name;
	}
}
