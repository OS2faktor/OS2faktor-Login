package dk.digitalidentity.service.model;

import java.security.KeyStore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeystoreEntry {
	private KeyStore keystore;
	private String password;
	
	public KeystoreEntry(KeyStore keystore, String password) {
		this.keystore = keystore;
		this.password = (password != null) ? password : "";
	}
}
