package dk.digitalidentity.api;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.KeystoreInfo;
import dk.digitalidentity.api.dto.KeystoreWithSwapDate;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.enums.SettingsKey;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.service.KeystoreService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CertManagerApi {

	@Autowired
	private KeystoreService keystoreService;
	
	@Autowired
	private SettingService settingService;

	@GetMapping("/api/certmanager/all")
	@ResponseBody
	public ResponseEntity<?> getAll() {
		List<Keystore> keystores = keystoreService.findAll();
		
		return ResponseEntity.ok(keystores.stream().filter(k -> !k.isDisabled()).map(k -> new KeystoreInfo(k)).collect(Collectors.toList()));
	}

	@PutMapping("/api/certmanager/swapPrimaryForIdp")
	@ResponseBody
	public ResponseEntity<?> swapPrimaryForIdP() {
		List<Keystore> keystores = keystoreService.findAll();
		
		// ensure that there are two certificates
		if (keystores.size() != 2) {
			return ResponseEntity.badRequest().body("Can only swap if there are two certificates, but there are currently " + keystores.size());
		}
		
		for (Keystore keystore : keystores) {
			keystore.setPrimaryForIdp(!keystore.isPrimaryForIdp());
			keystore.setLastUpdated(LocalDateTime.now());
		}
		
		keystoreService.saveAll(keystores);
		
		log.info("Swapped primary certificate for IdP");
		
		return ResponseEntity.ok().build();
	}
	
	@PutMapping("/api/certmanager/swapPrimaryForNemLogin")
	@ResponseBody
	public ResponseEntity<?> swapPrimaryForNemLogin() {
		List<Keystore> keystores = keystoreService.findAll();
		
		// ensure that there are two certificates
		if (keystores.size() != 2) {
			return ResponseEntity.badRequest().body("Can only swap if there are two certificates, but there are currently " + keystores.size());
		}
		
		for (Keystore keystore : keystores) {
			keystore.setPrimaryForNemLogin(!keystore.isPrimaryForNemLogin());
			keystore.setLastUpdated(LocalDateTime.now());
		}
		
		keystoreService.saveAll(keystores);
		
		log.info("Swapped primary certificate for IdP");
		
		return ResponseEntity.ok().build();
	}
	
	@PutMapping("/api/certmanager/disableSecondary")
	@ResponseBody
	public ResponseEntity<?> disableSecondary() {
		List<Keystore> keystores = keystoreService.findAll();
		
		// ensure that there are two certificates
		if (keystores.size() != 2) {
			return ResponseEntity.badRequest().body("Can only disable secondary if there are two certificates, but there are currently " + keystores.size());
		}
		
		for (Keystore keystore : keystores) {
			if (!keystore.isPrimaryForIdp() && !keystore.isPrimaryForNemLogin()) {
				keystore.setDisabled(true);
				keystore.setLastUpdated(LocalDateTime.now());
				
				keystoreService.save(keystore);

				log.info("Disabled secondary certificate");

				return ResponseEntity.ok().build();
			}
		}

		return ResponseEntity.badRequest().body("Can only disable secondary if both IdP and NemLogin uses the same certificate as secondary");
	}

	@PostMapping("/api/certmanager/newSecondary")
	@ResponseBody
	public ResponseEntity<?> loadNewSecondary(@RequestBody KeystoreWithSwapDate keystoreWithSwapDate) throws Exception {
		List<Keystore> keystores = keystoreService.findAll();

		Keystore secondary = null;
		if (keystores.size() > 1) {
			for (Keystore keystore : keystores) {
				if (!keystore.isPrimaryForIdp() && !keystore.isPrimaryForNemLogin()) {
					secondary = keystore;
					break;
				}
			}

			if (secondary == null) {
				return ResponseEntity.badRequest().body("Can only upload new secondary if both IdP and NemLogin uses the same certificate as existing secondary or no secondary exists");
			}
		}
		
		// throws exception if payload is not a valid PKCS#12 keystore with valid password
		Keystore ks = null;
		try {
			ks = getKeystoreFromPayload(keystoreWithSwapDate.getKeystore(), keystoreWithSwapDate.getPassword());			
		}
		catch (Exception ex) {
			log.error("Failed to load keystore", ex);
			return ResponseEntity.badRequest().body("Error parsing keystore: " + ex.getMessage());			
		}

		if (secondary == null) {
			secondary = new Keystore();
		}

		secondary.setDisabled(false);		
		secondary.setExpires(ks.getExpires());
		secondary.setKeystore(ks.getKeystore());
		secondary.setSubjectDn(ks.getSubjectDn());
		secondary.setPassword(keystoreWithSwapDate.getPassword());
		secondary.setPrimaryForIdp(false);
		secondary.setPrimaryForNemLogin(false);
		secondary.setLastUpdated(LocalDateTime.now());

		keystoreService.save(secondary);
		
		settingService.setLocalDateTimeSetting(SettingsKey.CERTIFICATE_ROLLOVER_TTS, keystoreWithSwapDate.getSwapDate());
		
		log.info("Loaded new secondary keystore (" + secondary.getSubjectDn() + ") with planned rollover at " + keystoreWithSwapDate.getSwapDate());
		
		return ResponseEntity.ok().build();
	}
	
	private Keystore getKeystoreFromPayload(String payload, String password) throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");

		byte[] content = Base64.getDecoder().decode(payload);
		ks.load(new ByteArrayInputStream(content), password.toCharArray());
		
		String alias = ks.aliases().nextElement();
		X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);
		String subject = certificate.getSubjectX500Principal().getName();
		subject = prettyPrint(subject);
		LocalDate expires = certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		Keystore keystore = new Keystore();
		keystore.setKeystore(content);
		keystore.setLastUpdated(LocalDateTime.now());
		keystore.setPassword(password);
		keystore.setPrimaryForIdp(false);
		keystore.setPrimaryForNemLogin(false);
		keystore.setExpires(expires);
		keystore.setSubjectDn(subject);

		return keystore;
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
