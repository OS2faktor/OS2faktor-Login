package dk.digitalidentity.service.dto.KombitJfr;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Jfr {
	private String identifier;
	private String cvr;

	public Jfr(String identifier, String cvr) {
		this.identifier = identifier;
		this.cvr = cvr;
	}
}
