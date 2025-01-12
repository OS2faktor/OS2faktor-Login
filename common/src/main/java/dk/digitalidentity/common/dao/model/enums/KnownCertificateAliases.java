package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum KnownCertificateAliases {
	OCES("enum.knowncertificatealias.oces"),
	OCES_SECONDARY("enum.knowncertificatealias.oces.secondary"),
	NEMLOGIN("enum.knowncertificatealias.nemlogin"),
	NEMLOGIN_SECONDARY("enum.knowncertificatealias.nemlogin.secondary"),
	SELFSIGNED("enum.knowncertificatealias.selfsigned");
	
	private String message;

	private KnownCertificateAliases(String message) {
		this.message = message;
	}
}