package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class LoginInfoMessageDTO {
	private String content;
	private boolean enabled;
}
