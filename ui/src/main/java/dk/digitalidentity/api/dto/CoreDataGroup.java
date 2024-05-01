package dk.digitalidentity.api.dto;

import java.util.List;
import java.util.stream.Collectors;

import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

@Getter
@Setter
@NoArgsConstructor
public class CoreDataGroup {
	private String uuid;
	private String name;
	private String description;
	private List<String> members;

	public CoreDataGroup(Group group) {
		this.uuid = group.getUuid();
		this.name = group.getName();
		this.description = group.getDescription();
		this.members = CollectionUtils.emptyIfNull(group.getMembers()).stream().map(Person::getLowerSamAccountName).collect(Collectors.toList());
	}
}
