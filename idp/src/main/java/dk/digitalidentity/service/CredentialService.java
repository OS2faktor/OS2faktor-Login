package dk.digitalidentity.service;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.HashMap;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Service
public class CredentialService {
	private BasicX509Credential basicX509Credential;
	private BasicX509Credential secondaryBasicX509Credential;
	private BasicX509Credential selfsignedX509Credential;

	@Autowired
	private KeystoreService keystoreService;

	public void evictCache() {
		basicX509Credential = null;
		secondaryBasicX509Credential = null;
		selfsignedX509Credential = null;
	}
	
	public BasicX509Credential getSecondaryBasicX509Credential() throws ResponderException {
		if (secondaryBasicX509Credential != null) {
			return secondaryBasicX509Credential;
		}

		KeyStore ks = keystoreService.getJavaKeystore(KnownCertificateAliases.OCES_SECONDARY.toString());
		if (ks == null) {
			return null;
		}

		Map<String, String> passwords = new HashMap<>();
		String alias = null;
		try {
			alias = ks.aliases().nextElement();
			passwords.put(alias, keystoreService.getJavaKeystorePassword(KnownCertificateAliases.OCES_SECONDARY.toString()));
		}
		catch (KeyStoreException ex) {
			throw new ResponderException("Keystore ikke initialiseret ordentligt", ex);
		}

		KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(ks, passwords);

		CriteriaSet criteria = new CriteriaSet();
		EntityIdCriterion entityIdCriterion = new EntityIdCriterion(alias);
		criteria.add(entityIdCriterion);

		try {
			secondaryBasicX509Credential = (BasicX509Credential) resolver.resolveSingle(criteria);

			return secondaryBasicX509Credential;
		}
		catch (ResolverException e) {
			throw new ResponderException("IDP kunne ikke finde egne credentials ud fra aliasset: " + alias, e);
		}
	}
	
	public BasicX509Credential getBasicX509Credential() throws ResponderException {
		if (basicX509Credential != null) {
			return basicX509Credential;
		}

		KeyStore ks = keystoreService.getJavaKeystore(KnownCertificateAliases.OCES.toString());
		if (ks == null) {
			return null;
		}

		Map<String, String> passwords = new HashMap<>();

		String alias = null;
		try {
			alias = ks.aliases().nextElement();

			passwords.put(alias, keystoreService.getJavaKeystorePassword(KnownCertificateAliases.OCES.toString()));
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

	public BasicX509Credential getSelfsignedX509Credential() throws ResponderException {
		if (selfsignedX509Credential != null) {
			return selfsignedX509Credential;
		}

		KeyStore ks = keystoreService.getJavaKeystore(KnownCertificateAliases.SELFSIGNED.toString());
		if (ks == null) {
			return null;
		}

		Map<String, String> passwords = new HashMap<>();
		String alias = null;
		try {
			alias = ks.aliases().nextElement();

			passwords.put(alias, keystoreService.getJavaKeystorePassword(KnownCertificateAliases.SELFSIGNED.toString()));
		}
		catch (KeyStoreException ex) {
			throw new ResponderException("Keystore ikke initialiseret ordentligt", ex);
		}

		KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(ks, passwords);

		CriteriaSet criteria = new CriteriaSet();
		EntityIdCriterion entityIdCriterion = new EntityIdCriterion(alias);
		criteria.add(entityIdCriterion);

		try {
			selfsignedX509Credential = (BasicX509Credential) resolver.resolveSingle(criteria);
			
			return selfsignedX509Credential;
		}
		catch (ResolverException e) {
			throw new ResponderException("IDP kunne ikke finde egne credentials ud fra aliasset: " + alias, e);
		}
	}

	public KeyInfo getSecondaryPublicKeyInfo() throws ResponderException {
		Credential credentials = getSecondaryBasicX509Credential();
		if (credentials == null) {
			return null;
		}
		
		X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
		x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
		KeyInfoGenerator keyInfoGenerator = x509KeyInfoGeneratorFactory.newInstance();

		try {
			return keyInfoGenerator.generate(credentials);
		}
		catch (SecurityException e) {
			throw new ResponderException("Kunne ikke generere public key ud fra IdP secondary credentials", e);
		}
	}

	public KeyInfo getSelfsignedPublicKeyInfo() throws ResponderException {
		X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
		x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
		KeyInfoGenerator keyInfoGenerator = x509KeyInfoGeneratorFactory.newInstance();

		try {
			return keyInfoGenerator.generate(getSelfsignedX509Credential());
		}
		catch (SecurityException e) {
			throw new ResponderException("Kunne ikke generere public key ud fra IdP credentials", e);
		}
	}
	
	public KeyInfo getPrimaryPublicKeyInfo() throws ResponderException {
		X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
		x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
		KeyInfoGenerator keyInfoGenerator = x509KeyInfoGeneratorFactory.newInstance();

		try {
			return keyInfoGenerator.generate(getBasicX509Credential());
		}
		catch (SecurityException e) {
			throw new ResponderException("Kunne ikke generere public key ud fra IdP credentials", e);
		}
	}
	
	public KeyInfo getPublicKeyInfo(BasicX509Credential credential) throws ResponderException {
		X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
		x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
		KeyInfoGenerator keyInfoGenerator = x509KeyInfoGeneratorFactory.newInstance();

		try {
			return keyInfoGenerator.generate(credential);
		}
		catch (SecurityException e) {
			throw new ResponderException("Kunne ikke generere public key ud fra IdP credentials", e);
		}
	}
}
