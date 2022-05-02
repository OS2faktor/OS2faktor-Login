package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatureDTO {
	private String name;
	private String description;
	private boolean enabled;
}
