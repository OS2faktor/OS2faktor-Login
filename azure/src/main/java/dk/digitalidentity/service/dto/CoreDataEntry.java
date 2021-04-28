package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class CoreDataEntry {
	private String uuid;
	private String cpr; // TODO
	private String name; // TODO compute
	private String email;
	private String samAccountName; // TODO
	private boolean nsisAllowed; // TODO config or member of group
	private Map<String, String> attributes; // TODO

	public CoreDataEntry() {
		this.attributes = new HashMap<>();
	}
}
