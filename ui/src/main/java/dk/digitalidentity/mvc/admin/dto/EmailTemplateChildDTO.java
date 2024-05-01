package dk.digitalidentity.mvc.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailTemplateChildDTO {
	private long id;
	private String title;
	private String message;
	private boolean enabled;
	private boolean emailEnabled;
	private boolean eboksEnabled;
	private long domainId;
}
