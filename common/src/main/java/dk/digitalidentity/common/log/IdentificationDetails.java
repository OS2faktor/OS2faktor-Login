package dk.digitalidentity.common.log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class IdentificationDetails {

	// id used to verify users identity
	@JsonProperty(value = "Identifikationsmiddel")
	private String idType;
	@JsonProperty(value = "Identifikationsmiddel ID")
	private String idSerial;
	@JsonProperty(value = "Supplerende identifikationsnoter")
	private String notes;
	
	// level of identification
	@JsonProperty(value = "Identifikationsniveau")
	private String nsisLevel;
	
	// only used for issuing password
	@JsonProperty(value = "Kodeord udleveret personligt")
	private String adminSeenPassword;

	// only used for issuing MFA
	@JsonProperty(value = "Type af 2-faktor enhed")
	private String mfaType;
	@JsonProperty(value = "ID på 2-faktor enhed")
	private String mfaId;
}
