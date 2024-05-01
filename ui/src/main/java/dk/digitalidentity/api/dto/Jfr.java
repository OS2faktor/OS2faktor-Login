package dk.digitalidentity.api.dto;

import dk.digitalidentity.common.dao.model.KombitJfr;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class Jfr {
	private String identifier;
	private String cvr;

	public Jfr(KombitJfr jfr) {
		this.identifier = jfr.getIdentifier();
		this.cvr = jfr.getCvr();
	}
}
