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
	private String subjectDn;
	private LocalDate expires;
	private LocalDateTime lastUpdated;
	private boolean kms;
	private String alias;
	
	public KeystoreInfo(Keystore keystore) {
		this.subjectDn = keystore.getSubjectDn();		
		this.expires = keystore.getExpires();
		this.lastUpdated = keystore.getLastUpdated();
		this.kms = keystore.isKms();
		this.alias = keystore.getAlias();
	}
}
