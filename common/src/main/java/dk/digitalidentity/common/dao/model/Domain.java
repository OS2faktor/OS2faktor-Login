package dk.digitalidentity.common.dao.model;

import java.util.List;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.envers.Audited;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Audited
@Table(name = "domains")
@Setter
@Getter
@NoArgsConstructor
public class Domain {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String name;

	@Column
	private boolean monitored;

	@ManyToOne
	@JsonIgnore
	@JoinColumn(name = "parent_domain_id", nullable = true)
	private Domain parent;

	@JsonIgnore
	@OneToMany(mappedBy = "parent")
	private List<Domain> childDomains;

	public Domain(String name) {
		this.name = name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Domain domain = (Domain) o;

		return (getId() == domain.getId()) && getName().equals(domain.getName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getId(), getName());
	}

	@Override
	public String toString() {
		if (parent != null) {
			return parent.getName() + " - " + getName();
		}

		return getName();
	}
}
