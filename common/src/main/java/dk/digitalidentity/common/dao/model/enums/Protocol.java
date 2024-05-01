package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum Protocol {
	SAML20("SAML"),
	OIDC10("OpenID Connect"),
	WSFED("WS Federation");

	private String prettyName;

	Protocol(String prettyName) {
		this.prettyName = prettyName;
	}
}
