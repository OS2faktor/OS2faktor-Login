package dk.digitalidentity.controller.wsfederation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true, includeFieldNames = true)
public class WSFedRequestDTO {

	// required for all
	private String wa;

	// required for login
	private String wtrealm;

	// optional login parameters
	private int wfresh;
	private String wauth;
	private String wreq;
	
	// common parameters
	private String reply; // some SPs use reply instead of wreply
	private String wreply;
	private String wres;
	private String wctx;
	private String wp;
	private String wct;
	private String wfed;
	private String wencoding;
}
