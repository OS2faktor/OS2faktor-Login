package dk.digitalidentity.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.Keystore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KeystoreInfo {
	private long id;
	private String subjectDn;
	private LocalDate expires;
	private LocalDateTime lastUpdated;
	private boolean primaryForIdp;
	private boolean primaryForNemLogin;
	
	public KeystoreInfo(Keystore keystore) {
		this.id = keystore.getId();
		this.subjectDn = keystore.getSubjectDn();		
		this.expires = keystore.getExpires();
		this.lastUpdated = keystore.getLastUpdated();
		this.primaryForIdp = keystore.isPrimaryForIdp();
		this.primaryForNemLogin = keystore.isPrimaryForNemLogin();
	}
}
