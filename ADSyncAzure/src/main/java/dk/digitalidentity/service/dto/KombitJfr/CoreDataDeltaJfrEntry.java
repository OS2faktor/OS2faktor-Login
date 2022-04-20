package dk.digitalidentity.service.dto.KombitJfr;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataDeltaJfrEntry {
	private String uuid;
	private Set<Jfr> addJfrs;
	private Set<Jfr> removeJfrs;

	public CoreDataDeltaJfrEntry(String uuid, Set<Jfr> addJfrs, Set<Jfr> removeJfrs) {
		this.uuid = uuid;
		this.addJfrs = addJfrs;
		this.removeJfrs = removeJfrs;
	}
}
