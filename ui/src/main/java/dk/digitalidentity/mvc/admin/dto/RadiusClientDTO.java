package dk.digitalidentity.mvc.admin.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Objects;

import dk.digitalidentity.common.dao.model.RadiusClient;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RadiusClientConditionType;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ConditionDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RadiusClientDTO {
	private long id;
	private String name;
	private String password;
	private String ipAddress;
	private List<ConditionDTO> conditionsDomains;
	private List<ConditionDTO> conditionsGroups;
	private List<RadiusClaimDTO> claims;
	private ConditionDTO conditionWithAttribute;
	private NSISLevel nsisLevelRequired;

	public RadiusClientDTO() {
		this.conditionsDomains = new ArrayList<>();
		this.conditionsGroups = new ArrayList<>();
		this.claims = new ArrayList<>();
	}

	public RadiusClientDTO(RadiusClient radiusClient) {
		this.id = radiusClient.getId();
		this.name = radiusClient.getName();
		this.password = radiusClient.getPassword();
		this.ipAddress = radiusClient.getIpAddress();
		this.nsisLevelRequired = radiusClient.getNsisLevelRequired();

		this.claims = radiusClient.getClaims()
				.stream()
				.map(RadiusClaimDTO::new)
				.collect(Collectors.toList());
		
		this.conditionsDomains = radiusClient.getConditions()
				.stream()
				.filter(condition -> RadiusClientConditionType.DOMAIN.equals(condition.getType()))
				.map(ConditionDTO::new)
				.collect(Collectors.toList());

		this.conditionsGroups = radiusClient.getConditions()
				.stream()
				.filter(condition -> RadiusClientConditionType.GROUP.equals(condition.getType()))
				.map(ConditionDTO::new)
				.collect(Collectors.toList());

		this.conditionWithAttribute = null;

		radiusClient.getConditions()
				.stream()
				.filter(condition -> RadiusClientConditionType.WITH_ATTRIBUTE.equals(condition.getType()))
				.findAny()
				.ifPresent(radiusClientCondition -> this.conditionWithAttribute = new ConditionDTO(radiusClientCondition));
	}
	
	// helper method for UI
	public boolean hasClaim(String personField) {
		return this.claims.stream().anyMatch(c -> Objects.equal(personField, c.getPersonField()));
	}

	// helper method for UI
	public long getClaimValue(String personField) {
		RadiusClaimDTO claim = this.claims.stream().filter(c -> Objects.equal(personField, c.getPersonField())).findFirst().orElse(null);
		if (claim != null) {
			return claim.getAttributeId();
		}
		
		return 0;
	}
}
