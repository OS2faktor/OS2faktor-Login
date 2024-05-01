package dk.digitalidentity.api.dto;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.Person;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoreDataFullJfrEntry {
	private String samAccountName;
	private String uuid;
	private Set<Jfr> jfrs;

	public CoreDataFullJfrEntry(Person person) {
		this.samAccountName = person.getSamaccountName();
		this.uuid = person.getUuid();
		this.jfrs = CollectionUtils.emptyIfNull(person.getKombitJfrs()).stream().map(Jfr::new).collect(Collectors.toSet());
	}

	@JsonIgnore
	public String getLowerSamAccountName() {
		if (samAccountName != null) {
			return samAccountName.toLowerCase();
		}

		return null;
	}
}
