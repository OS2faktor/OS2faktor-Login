package dk.digitalidentity.service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import dk.digitalidentity.aws.kms.jce.provider.KmsProvider;
import dk.digitalidentity.common.dao.KeystoreDao;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.MetadataController;
import dk.digitalidentity.samlmodule.service.DISAML_CredentialService;
import dk.digitalidentity.service.model.KeystoreEntry;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

@Slf4j
@EnableScheduling
@Service
public class KeystoreService {
	private LocalDateTime lastLoaded = LocalDateTime.of(1970, 1, 1, 0, 0);
	private boolean initialized = false, classInitialized = false;
	private Map<String, KeystoreEntry> keyStoreMap = new HashMap<>();

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
	
	public List<String> getAliases() {
		ensureInitialized();
		
		return keyStoreMap.keySet().stream().sorted().collect(Collectors.toList());
	}

	public KeyStore getJavaKeystore(String alias) {
		ensureInitialized();

		KeystoreEntry entry = keyStoreMap.get(alias);
		if (entry != null) {
			return entry.getKeystore();
		}
		
		return null;
	}
	
	public String getJavaKeystorePassword(String alias) {
		ensureInitialized();
		
		KeystoreEntry entry = keyStoreMap.get(alias);
		if (entry != null) {
			return entry.getPassword();
		}
		
		log.debug("Missing keystore with alias " + alias);
		
		return null;
	}
	
	public String getKmsAlias(String alias) {
		ensureInitialized();
		
		KeystoreEntry entry = keyStoreMap.get(alias);
		if (entry != null) {
			return entry.getKmsAlias();
		}
		
		log.debug("Missing keystore with alias " + alias);
		
		return null;
	}

	@SuppressWarnings("deprecation")
	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		// make sure KMS provider is loaded
		KmsClient kmsClient = KmsClient.builder().region(Region.EU_WEST_1).build();
		KmsProvider kmsProvider = new KmsProvider(kmsClient);			
		Security.addProvider(kmsProvider);

		// bootstrap database if empty (selfsigned certificate does not count)
		List<Keystore> keystores = keystoreDao.findAll().stream()
				.filter(k -> !Objects.equals(k.getAlias(), KnownCertificateAliases.SELFSIGNED.toString()))
				.collect(Collectors.toList());

		if (keystores.size() == 0) {
			Keystore primaryKeystore = getKeystoreFromConfiguration(configuration.getKeystore().getLocation(), configuration.getKeystore().getPassword(), true);
			
			// TODO: this code can go away when we move 100% to KMS created keystores
			// make sure we have a NemLog-in Keystore as well			
			if (primaryKeystore != null) {
				keystoreDao.save(primaryKeystore);
				
				// save a copy of the same information for the NemLogin keystore
				primaryKeystore.setId(0);
				primaryKeystore.setAlias(KnownCertificateAliases.NEMLOGIN.toString());
				keystoreDao.save(primaryKeystore);
			}
		}
		else {
			// TODO: this code can go away when we move 100% to KMS created keystores
			// make sure we have a NemLog-in Keystore as well
			if (!keystores.stream().anyMatch(k -> Objects.equals(k.getAlias(), KnownCertificateAliases.NEMLOGIN.toString()))) {
				Keystore oces = keystores.stream().filter(k -> Objects.equals(k.getAlias(), KnownCertificateAliases.OCES.toString())).findFirst().orElse(null);
				if (oces != null) {
					// save a non-KMS version of this keystore
					oces.setId(0);
					oces.setKms(false);
					oces.setKmsAlias(null);
					oces.setAlias(KnownCertificateAliases.NEMLOGIN.toString());
					keystoreDao.save(oces);					
				}
				else {
					log.error("Unable to find an OCES keystore in keystores table to migrate to a NemLog-in keystore");
				}
			}			
		}
		
		classInitialized = true;

		loadKeystores();
	}
	
	@Scheduled(cron = "0 0/5 * * * ?")
	public synchronized void loadKeystores() {
		// wait till KMS is loaded
		if (classInitialized == false) {
			return;
		}

		LocalDateTime newReload = LocalDateTime.now();
		
		List<Keystore> keystores = keystoreDao.findByLastUpdatedAfter(lastLoaded);
		boolean changes = false;

		// load new or reload modified
		for (Keystore keystore : keystores) {
			if (keystore.isDisabled()) {
				if (keyStoreMap.containsKey(keystore.getAlias())) {
					keyStoreMap.remove(keystore.getAlias());

					log.info("Removing " + keystore.getAlias() + " from keystore map");
					
					changes = true;
				}

				continue;
			}

			log.info("Reloading keystore: " + keystore.getSubjectDn());
			changes = true;

			KeyStore ks = loadKeystore(keystore);
			keyStoreMap.put(keystore.getAlias(), new KeystoreEntry(ks, keystore.getPassword(), keystore.getKmsAlias()));
		}
		
		if (changes) {
			metadataController.evictCache();
			credentialService.evictCache();
			diSamlCredentialService.reset();
		}

		lastLoaded = newReload;
		initialized = true;
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
			if (keystore.isKms()) {
		        ByteArrayInputStream inputStream = new ByteArrayInputStream(keystore.getCertificate());
		        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

				Certificate certificate = certificateFactory.generateCertificate(inputStream);
				
				KeyStore keyStore = KeyStore.getInstance("KMS");
				keyStore.load(new ByteArrayInputStream(certificate.getEncoded()), null);
				
				return keyStore;
			}
			else {
				KeyStore keyStore = KeyStore.getInstance("PKCS12");

				keyStore.load(new ByteArrayInputStream(keystore.getKeystore()), keystore.getPassword().toCharArray());

				return keyStore;
			}
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
	
	private Keystore getKeystoreFromConfiguration(String location, String password, boolean primary) {
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
			keystore.setAlias(KnownCertificateAliases.OCES.toString());
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
