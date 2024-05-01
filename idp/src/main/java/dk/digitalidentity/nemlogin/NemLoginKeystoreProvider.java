package dk.digitalidentity.nemlogin;

import java.security.KeyStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.samlmodule.service.DISAML_KeystoreProvider;
import dk.digitalidentity.service.KeystoreService;

@Component
public class NemLoginKeystoreProvider implements DISAML_KeystoreProvider {
	
	@Autowired
	private KeystoreService keystoreService;
	
	@Override
	public KeyStore getPrimaryKeystore() {
		return keystoreService.getPrimaryKeystoreForNemLogin();
	}

	@Override
	public String getPrimaryKeystorePassword() {
		return keystoreService.getPrimaryKeystoreForNemLoginPassword();
	}

	@Override
	public KeyStore getSecondaryKeystore() {
		return keystoreService.getSecondaryKeystoreForNemLogin();
	}

	@Override
	public String getSecondaryKeystorePassword() {
		return keystoreService.getSecondaryKeystoreForNemLoginPassword();
	}
}
