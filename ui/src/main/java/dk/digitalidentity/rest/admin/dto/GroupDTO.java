package dk.digitalidentity.rest.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupDTO {
	private long id;
	private String name;
	private String description;
}
