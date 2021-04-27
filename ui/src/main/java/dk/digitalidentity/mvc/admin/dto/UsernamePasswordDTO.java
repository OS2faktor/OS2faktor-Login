package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsernamePasswordDTO {
	private String userId;
	private String password;

	public UsernamePasswordDTO(String userId, String password) {
		this.userId = userId;
		this.password = password;
	}
}
