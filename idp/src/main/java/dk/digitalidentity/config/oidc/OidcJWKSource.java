package dk.digitalidentity.config.oidc;

import java.io.IOException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;

import dk.digitalidentity.service.KeystoreService;
import dk.digitalidentity.service.KeystoreService.KNOWN_CERTIFICATE_ALIASES;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OidcJWKSource implements JWKSource<SecurityContext> {
	public static final String KEYID = "oidc-key-1";

	@Autowired
	private KeystoreService keystoreService;

	@Override
	public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) throws KeySourceException {
		KeyPair keyPair = null;
		MessageDigest sha256 = null;
		byte[] encodedCert = null;

		try {
			sha256 = MessageDigest.getInstance("SHA-256");
			keyPair = getKeyPair();
			X509Certificate cert = getCertificate();
			encodedCert = cert.getEncoded();
		}
		catch (CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
			log.error("GetKeyPair error", e);
			throw new RuntimeException(e);
		}

		RSAKey rsaKey = new RSAKey
				.Builder((RSAPublicKey) keyPair.getPublic())
				.keyID(KEYID)
				.keyUse(KeyUse.SIGNATURE)
				.x509CertSHA256Thumbprint(Base64URL.encode(sha256.digest(encodedCert)))
				.x509CertChain(Collections.singletonList(Base64.encode(encodedCert)))
				.privateKey((RSAPrivateKey) keyPair.getPrivate())
				.build();

		JWKSet jwkSet = new JWKSet(rsaKey);

		return jwkSelector.select(jwkSet);
	}
	
	public KeyPair getKeyPair() throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
		KeyStore ks = keystoreService.getJavaKeystore(KNOWN_CERTIFICATE_ALIASES.OCES.toString());
		String alias = ks.aliases().nextElement();

		String pwd = keystoreService.getJavaKeystorePassword(KNOWN_CERTIFICATE_ALIASES.OCES.toString());
		
		Key key = ks.getKey(alias, (pwd != null) ? pwd.toCharArray() : null);
		if (key instanceof PrivateKey) {
			return new KeyPair(ks.getCertificate(alias).getPublicKey(), (PrivateKey) key);
		}
		
		return null;
	}
	
	private X509Certificate getCertificate() throws KeyStoreException {
		KeyStore ks = keystoreService.getJavaKeystore(KNOWN_CERTIFICATE_ALIASES.OCES.toString());
		String alias = ks.aliases().nextElement();

		return (X509Certificate) ks.getCertificate(alias);
	}
}
