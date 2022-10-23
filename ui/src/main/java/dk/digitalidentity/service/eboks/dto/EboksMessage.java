package dk.digitalidentity.service.eboks.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EboksMessage {
	private String cpr;
	private String cvr;
	private String subject;
	private String municipalityName;
	private String content;
}
