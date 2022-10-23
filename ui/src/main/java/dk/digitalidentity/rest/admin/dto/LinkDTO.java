package dk.digitalidentity.rest.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinkDTO {
	private String text;
	private String link;
	private Long domainId;
	private String description;
}
