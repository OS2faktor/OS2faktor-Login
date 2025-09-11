package dk.digitalidentity.common.dao.model;

import java.util.List;
import java.util.Objects;

import org.hibernate.envers.Audited;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	@JoinColumn(name = "parent_domain_id", nullable = true)
	private Domain parent;

	@JsonIgnore
	@OneToMany(mappedBy = "parent")
	private List<Domain> childDomains;

	@Column
	private String nemLoginUserSuffix;

	@Column
	private String roleCatalogueDomain;

	@Column
	private boolean standalone;

	@Column
	private boolean nonNsis;

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
	
	public String getNemLoginDomain() {
		if (nemLoginUserSuffix == null) {
			return null;
		}
		else if (nemLoginUserSuffix.length() == 0) {
			// the first few municipalities ended up without a domain on their primary domain
			return "";
		}

		return nemLoginUserSuffix.startsWith("@") ? nemLoginUserSuffix : ("@" + nemLoginUserSuffix);
	}
}
