package dk.digitalidentity.common.dao.model;

import java.util.Set;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
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

	@Column
	@NotNull
	@Enumerated(EnumType.STRING)
	private NSISLevel nsisLevelRequired = NSISLevel.NONE;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "client")
	private Set<RadiusClientCondition> conditions;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "client")
	private Set<RadiusClientClaim> claims;

	public void loadFully() {
		this.claims.size();
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
