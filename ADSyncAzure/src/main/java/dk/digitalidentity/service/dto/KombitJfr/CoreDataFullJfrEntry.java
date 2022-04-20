package dk.digitalidentity.service.dto.KombitJfr;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataFullJfrEntry {
	private String uuid;
	private Set<Jfr> jfrs;

	public CoreDataFullJfrEntry(String uuid, Set<Jfr> jfrs) {
		this.uuid = uuid;
		this.jfrs = jfrs;
	}
}
