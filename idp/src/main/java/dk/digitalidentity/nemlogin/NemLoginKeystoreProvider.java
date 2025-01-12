package dk.digitalidentity.nemlogin;

import java.security.KeyStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.samlmodule.service.DISAML_KeystoreProvider;
import dk.digitalidentity.service.KeystoreService;

@Component
public class NemLoginKeystoreProvider implements DISAML_KeystoreProvider {
	
	@Autowired
	private KeystoreService keystoreService;
	
	@Override
	public KeyStore getPrimaryKeystore() {
		return keystoreService.getJavaKeystore(KnownCertificateAliases.NEMLOGIN.toString());
	}

	@Override
	public String getPrimaryKeystorePassword() {
		return keystoreService.getJavaKeystorePassword(KnownCertificateAliases.NEMLOGIN.toString());
	}

	@Override
	public KeyStore getSecondaryKeystore() {
		return keystoreService.getJavaKeystore(KnownCertificateAliases.NEMLOGIN_SECONDARY.toString());
	}

	@Override
	public String getSecondaryKeystorePassword() {
		return keystoreService.getJavaKeystorePassword(KnownCertificateAliases.NEMLOGIN_SECONDARY.toString());
	}
	
	/* TODO: only needed if/when we move NL3 serviceprovider keys to KMS
	@Override
	public String getPrimaryKeystoreAlias() {
		return keystoreService.getKmsAlias(KNOWN_CERTIFICATE_ALIASES.OCES.toString());
	}
	
	@Override
	public String getSecondaryKeystoreAlias() {
		return keystoreService.getKmsAlias(KNOWN_CERTIFICATE_ALIASES.OCES_SECONDARY.toString());
	}
	*/
}
