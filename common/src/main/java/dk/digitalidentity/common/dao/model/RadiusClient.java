package dk.digitalidentity.common.dao.model;

import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "radius_clients")
@Setter
@Getter
public class RadiusClient {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@NotNull
	@Column
	private String name;
	
	@NotNull
	@Column
	private String password;
	
	@NotNull
	@Column
	private String ipAddress;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "client")
	private Set<RadiusClientCondition> conditions;

	public void loadFully() {
		this.conditions.size();
		this.conditions.forEach(conditions -> {
			if (conditions.getDomain() != null) {
				conditions.getDomain().getName();
				conditions.getDomain().getChildDomains().size();
			}

			if (conditions.getGroup() != null) {
				conditions.getGroup().getName();
				conditions.getGroup().getDomain().getName();
				conditions.getGroup().getMemberMapping().size();
				conditions.getGroup().getMembers().size();
			}

			conditions.getType();
		});
	}
}
