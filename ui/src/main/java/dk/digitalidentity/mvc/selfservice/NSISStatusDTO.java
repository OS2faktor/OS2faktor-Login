package dk.digitalidentity.mvc.selfservice;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NSISStatusDTO {
	private NSISStatus nsisStatus;
	private String message;
}
