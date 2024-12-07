package dk.digitalidentity.service.model;

import java.security.KeyStore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeystoreEntry {
	private KeyStore keystore;
	private String password;
	private String kmsAlias;
	
	public KeystoreEntry(KeyStore keystore, String password, String kmsAlias) {
		this.keystore = keystore;
		this.kmsAlias = kmsAlias;
		this.password = (password != null) ? password : "";
	}
}
