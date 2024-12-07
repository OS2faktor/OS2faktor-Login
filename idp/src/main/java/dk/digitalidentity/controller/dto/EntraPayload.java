package dk.digitalidentity.controller.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EntraPayload implements Serializable {
	private static final long serialVersionUID = 6015134803371523049L;

	private String upn;
	private String state;
	private String nonce;
	private String audience;
	private String subject;
	private String redirectUrl;
	private String acr;
}
