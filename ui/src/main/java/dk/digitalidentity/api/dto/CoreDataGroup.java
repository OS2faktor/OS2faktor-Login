package dk.digitalidentity.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataGroup {
	private String uuid;
	private String name;
	private String description;
	private List<String> members;
}
