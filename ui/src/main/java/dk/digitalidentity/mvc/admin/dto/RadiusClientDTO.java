package dk.digitalidentity.mvc.admin.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dk.digitalidentity.common.dao.model.RadiusClient;
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
	private ConditionDTO conditionWithAttribute;

	public RadiusClientDTO() {
		this.conditionsDomains = new ArrayList<>();
		this.conditionsGroups = new ArrayList<>();
	}

	public RadiusClientDTO(RadiusClient radiusClient) {
		this.id = radiusClient.getId();
		this.name = radiusClient.getName();
		this.password = radiusClient.getPassword();
		this.ipAddress = radiusClient.getIpAddress();


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
}
