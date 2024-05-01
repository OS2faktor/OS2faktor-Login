package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.Domain;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DomainDTO {
	private long id;
	private String name;

	public DomainDTO(Domain domain) {
		this.id = domain.getId();
		this.name = domain.getName();
	}
}
