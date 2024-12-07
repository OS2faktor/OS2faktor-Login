package dk.digitalidentity.common.dao.model.enums;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public enum Protocol {
	SAML20("SAML"),
	OIDC10("OpenID Connect"),
	WSFED("WS Federation"),
	ENTRAMFA("EntraID MFA");

	private String prettyName;

	Protocol(String prettyName) {
		this.prettyName = prettyName;
	}
	
	// UI should not allow picking EntraMFA
	public static List<Protocol> getAllowedValues() {
		List<Protocol> result = new ArrayList<>();
		result.add(SAML20);
		result.add(OIDC10);
		result.add(WSFED);
		
		return result;
	}
}
