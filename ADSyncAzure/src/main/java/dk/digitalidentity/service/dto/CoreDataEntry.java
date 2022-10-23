package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
public class CoreDataEntry {
	private String uuid;
	private String cpr;
	private String name;
	private String email;
	private String samAccountName;
	private boolean nsisAllowed;
	private Map<String, String> attributes;
	private boolean transferToNemlogin;
	private String rid;

	@JsonIgnore
	private transient String azureInternalId;
	
	public CoreDataEntry() {
		this.attributes = new HashMap<>();
	}
}
