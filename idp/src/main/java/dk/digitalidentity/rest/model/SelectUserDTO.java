package dk.digitalidentity.rest.model;

import dk.digitalidentity.common.dao.model.Domain;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SelectUserDTO {
	private long id;
	private String username;
	private String name;
	private Domain domain;
}
