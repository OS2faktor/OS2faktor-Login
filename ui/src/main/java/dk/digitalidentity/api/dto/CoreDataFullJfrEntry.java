package dk.digitalidentity.api.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataFullJfrEntry {
	private String samAccountName;
	private String uuid;
	private Set<Jfr> jfrs;

	@JsonIgnore
	public String getLowerSamAccountName() {
		if (samAccountName != null) {
			return samAccountName.toLowerCase();
		}

		return null;
	}
}
