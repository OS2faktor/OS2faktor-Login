package dk.digitalidentity.service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.KeystoreDao;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.MetadataController;
import dk.digitalidentity.samlmodule.service.DISAML_CredentialService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableScheduling
@Service
public class KeystoreService {
	private LocalDateTime lastLoaded = LocalDateTime.of(1970, 1, 1, 0, 0);
	private boolean initialized = false;

	private KeyStore primaryKeystoreForIdp;
	private KeyStore secondaryKeystoreForIdp;
	private KeyStore primaryKeystoreForNemLogin;
	private KeyStore secondaryKeystoreForNemLogin;
	
	@Getter
	private String primaryKeystoreForIdpPassword;
	
	@Getter
	private String secondaryKeystoreForIdpPassword;
	
	@Getter
	private String primaryKeystoreForNemLoginPassword;
	
	@Getter
	private String secondaryKeystoreForNemLoginPassword;

	@Autowired
	private KeystoreDao keystoreDao;
	
	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private MetadataController metadataController;
	
	@Autowired
	private CredentialService credentialService;
	
	@Autowired
	private DISAML_CredentialService diSamlCredentialService;
	
	@SuppressWarnings("deprecation")
	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		// bootstrap database if empty
		if (keystoreDao.findAll().size() == 0) {
			Keystore primaryKeystore = getKeystoreFromConfigration(configuration.getKeystore().getLocation(), configuration.getKeystore().getPassword(), true);
			if (primaryKeystore != null) {
				keystoreDao.save(primaryKeystore);
			}
			
			Keystore secondaryKeystore = getKeystoreFromConfigration(configuration.getKeystore().getSecondaryLocation(), configuration.getKeystore().getSecondaryPassword(), false);
			if (secondaryKeystore != null) {
				keystoreDao.save(secondaryKeystore);
			}
		}

		loadKeystores();
	}
	
	@Scheduled(cron = "0 0/5 * * * ?")
	public synchronized void loadKeystores() {
		LocalDateTime newReload = LocalDateTime.now();
		
		List<Keystore> keystores = keystoreDao.findByLastUpdatedAfter(lastLoaded);
		boolean changes = false;
		
		for (Keystore keystore : keystores) {
			log.info("Reloading keystore: " + keystore.getSubjectDn());
			changes = true;

			KeyStore ks = loadKeystore(keystore);
			
			if (keystore.isPrimaryForIdp()) {
				log.info("Setting keystore as primary for IdP: " + keystore.getSubjectDn());
				
				primaryKeystoreForIdp = ks;
				primaryKeystoreForIdpPassword = keystore.getPassword();
			}
			else {
				if (keystore.isDisabled()) {
					log.info("Secondary keystore for IdP is disabled: " + keystore.getSubjectDn());
					
					secondaryKeystoreForIdp = null;
					secondaryKeystoreForIdpPassword = null;
				}
				else {
					log.info("Setting keystore as secondary for IdP: " + keystore.getSubjectDn());
	
					secondaryKeystoreForIdp = ks;
					secondaryKeystoreForIdpPassword = keystore.getPassword();
				}
			}
			
			if (keystore.isPrimaryForNemLogin()) {
				log.info("Setting keystore as primary for NemLogin: " + keystore.getSubjectDn());

				primaryKeystoreForNemLogin = ks;
				primaryKeystoreForNemLoginPassword = keystore.getPassword();
			}
			else {
				if (keystore.isDisabled()) {
					log.info("Secondary keystore for NemLogin is disabled: " + keystore.getSubjectDn());
					
					secondaryKeystoreForNemLogin = null;
					secondaryKeystoreForNemLoginPassword = null;
				}
				else {
					log.info("Setting keystore as secondary for NemLogin: " + keystore.getSubjectDn());
	
					secondaryKeystoreForNemLogin = ks;
					secondaryKeystoreForNemLoginPassword = keystore.getPassword();
				}
			}
		}

		if (changes) {
			metadataController.evictCache();
			credentialService.evictCache();
			diSamlCredentialService.reset();
		}

		lastLoaded = newReload;
		initialized = true;
	}
	
	public KeyStore getPrimaryKeystoreForIdp() {
		ensureInitialized();
		
		return primaryKeystoreForIdp;
	}

	public KeyStore getPrimaryKeystoreForNemLogin() {
		ensureInitialized();
		
		return primaryKeystoreForNemLogin;
	}

	public KeyStore getSecondaryKeystoreForIdp() {
		ensureInitialized();
		
		return secondaryKeystoreForIdp;
	}

	public KeyStore getSecondaryKeystoreForNemLogin() {
		ensureInitialized();
		
		return secondaryKeystoreForNemLogin;
	}

	private void ensureInitialized() {
		if (!initialized) {
			int counter = 30;

			// wait up to 3 seconds for database initialization, and then fail
			while (--counter > 0) {
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException ignored) {
					;
				}
				
				if (initialized) {
					break;
				}
			}
		}
	}
	
	private KeyStore loadKeystore(Keystore keystore) {
		try {
			KeyStore keyStore = KeyStore.getInstance("PKCS12");

			keyStore.load(new ByteArrayInputStream(keystore.getKeystore()), keystore.getPassword().toCharArray());
			
			return keyStore;
		}
		catch (Exception ex) {
			log.error("Failed to initialize keystore: " + keystore.getSubjectDn(), ex);
			return null;
		}
	}
	
	private byte[] readFile(String location) {
		if (!StringUtils.hasLength(location)) {
			return null;
		}
		
		try {
			return Files.readAllBytes(ResourceUtils.getFile(location).toPath());
		}
		catch (Exception ex) {
			log.error("Failed to load file: " + location, ex);
			return null;
		}
	}
	
	private Keystore getKeystoreFromConfigration(String location, String password, boolean primary) {
		byte[] content = readFile(location);
		if (content != null) {
			String subject = null;
			LocalDate expires = null;
			
			try {
				KeyStore ks = KeyStore.getInstance("PKCS12");

				ks.load(new ByteArrayInputStream(content), password.toCharArray());
				
				String alias = ks.aliases().nextElement();
				X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);
				subject = certificate.getSubjectX500Principal().getName();
				subject = prettyPrint(subject);
				expires = certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			}
			catch (Exception ex) {
				log.error("Failed to initialize keystore loaded from: " + location, ex);
				return null;
			}

			Keystore keystore = new Keystore();
			keystore.setKeystore(content);
			keystore.setLastUpdated(LocalDateTime.now());
			keystore.setPassword(password);
			keystore.setPrimaryForIdp(primary);
			keystore.setPrimaryForNemLogin(primary);
			keystore.setExpires(expires);
			keystore.setSubjectDn(subject);

			return keystore;
		}

		return null;
	}
	
	private String prettyPrint(String subject) {
		try {
			int cnIdx = subject.indexOf("CN=") + 3;
			int commaIdx = subject.indexOf(",", cnIdx);
			
			if (cnIdx < commaIdx) {
				return subject.substring(cnIdx, commaIdx);
			}
			else if (cnIdx >= 3) {
				return subject.substring(cnIdx);
			}
		}
		catch (Exception ex) {
			log.info("Failed to pretty print subject = " + subject + " due to error = " + ex.getMessage());
		}
		
		return subject;
	}
}
