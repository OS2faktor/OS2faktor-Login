package dk.digitalidentity.common.dao.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

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
