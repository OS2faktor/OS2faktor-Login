package dk.digitalidentity.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Service
public class CredentialService {
	private BasicX509Credential basicX509Credential;

	@Autowired
	private OS2faktorConfiguration configuration;

	public BasicX509Credential getBasicX509Credential() throws ResponderException {
		if (basicX509Credential != null) {
			return basicX509Credential;
		}

		KeyStore ks = keyStore(configuration.getKeystore().getLocation(), configuration.getKeystore().getPassword().toCharArray());
		Map<String, String> passwords = new HashMap<>();
		String alias = null;
		try {
			alias = ks.aliases().nextElement();
			passwords.put(alias, configuration.getKeystore().getPassword());
		}
		catch (KeyStoreException ex) {
			throw new ResponderException("Keystore ikke initialiseret ordentligt", ex);
		}

		KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(ks, passwords);

		CriteriaSet criteria = new CriteriaSet();
		EntityIdCriterion entityIdCriterion = new EntityIdCriterion(alias);
		criteria.add(entityIdCriterion);

		try {
			basicX509Credential = (BasicX509Credential) resolver.resolveSingle(criteria);
			return basicX509Credential;
		}
		catch (ResolverException e) {
			throw new ResponderException("IDP kunne ikke finde egne credentials ud fra aliasset: " + alias, e);
		}
	}

	public KeyInfo getPublicKeyInfo() throws ResponderException {
		X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
		x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
		KeyInfoGenerator keyInfoGenerator = x509KeyInfoGeneratorFactory.newInstance();

		try {
			return keyInfoGenerator.generate(getBasicX509Credential());
		}
		catch (SecurityException e) {
			throw new ResponderException("Kunne ikke generere public key ud fra IPD credentials", e);
		}
	}

	private KeyStore keyStore(String file, char[] password) throws ResponderException {
		try {
			KeyStore keyStore = KeyStore.getInstance("PKCS12");

			File key = ResourceUtils.getFile(file);

			InputStream in = new FileInputStream(key);
			keyStore.load(in, password);
			return keyStore;
		}
		catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
			throw new ResponderException("Kunne ikke tilgå IDP credentials", e);
		}
	}
}
