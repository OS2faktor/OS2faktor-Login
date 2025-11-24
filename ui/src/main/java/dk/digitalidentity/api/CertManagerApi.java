package dk.digitalidentity.api;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.KeystoreInfo;
import dk.digitalidentity.api.dto.KeystorePayload;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.CertificateChangelogService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.service.KeystoreService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CertManagerApi {

	@Autowired
	private KeystoreService keystoreService;
	
	@Autowired
	private CertificateChangelogService certificateChangelogService;
	
	@Autowired
	private SettingService settingService;

	@GetMapping("/api/certmanager/all")
	@ResponseBody
	public ResponseEntity<?> getAll() {
		List<Keystore> keystores = keystoreService.findAll();
		
		return ResponseEntity.ok(keystores.stream().filter(k -> !k.isDisabled()).map(k -> new KeystoreInfo(k)).collect(Collectors.toList()));
	}

	@PutMapping("/api/certmanager/disable/{alias}")
	@ResponseBody
	public ResponseEntity<?> disableCertificate(@RequestParam String operatorId, @PathVariable("alias") String alias) {
		if (!Objects.equals(alias, KnownCertificateAliases.NEMLOGIN_SECONDARY.toString()) && !Objects.equals(alias, KnownCertificateAliases.OCES_SECONDARY.toString())) {
			return ResponseEntity.badRequest().body("Only allowed aliases are: NEMLOGIN_SECONDARY and OCES_SECONDARY");
		}

		Keystore keystore = keystoreService.findByAlias(alias);
		if (keystore == null) {
			log.warn("No certificate found with alias: " + alias);
			return ResponseEntity.notFound().build();
		}

		log.info("Disabled " + keystore.getSubjectDn());
		certificateChangelogService.deleteCertificate(operatorId, "Deleted " + keystore.getSubjectDn());

		keystore.setDisabled(true);
		keystore.setLastUpdated(LocalDateTime.now());
		keystoreService.save(keystore);

		return ResponseEntity.ok().build();
	}

	@PostMapping("/api/certmanager/new")
	@ResponseBody
	public ResponseEntity<?> loadCertificate(@RequestParam(name = "operatorId") String operatorId, @RequestBody KeystorePayload keystorePayload) throws Exception {
		List<Keystore> keystores = keystoreService.findAll();

		if (!keystorePayload.getRolloverTts().isAfter(LocalDateTime.now())) {
			log.warn("Cannot plan a rollover in the past: " + keystorePayload.getRolloverTts());

			return ResponseEntity.badRequest().body("Cannot plan a rollover in the past: " + keystorePayload.getRolloverTts());
		}
		
		// make sure we have both a primary and a secondary available
		Keystore secondaryNL = null, secondaryOCES = null;
		for (Keystore keystore : keystores) {
			if (keystore.getAlias().equals(KnownCertificateAliases.NEMLOGIN_SECONDARY.toString())) {
				secondaryNL = keystore;
			}
			else if (keystore.getAlias().equals(KnownCertificateAliases.OCES_SECONDARY.toString())) {
				secondaryOCES = keystore;
			}
		}

		if (secondaryNL == null) {
			secondaryNL = new Keystore();
			secondaryNL.setAlias(KnownCertificateAliases.NEMLOGIN_SECONDARY.toString());
		}

		// clear existing data
		secondaryNL.setDisabled(false);
		secondaryNL.setKeystore(null);
		secondaryNL.setPassword(null);
		secondaryNL.setLastUpdated(LocalDateTime.now());

		// load new data
		getKeystoreFromPfx(secondaryNL, keystorePayload.getKeystore(), keystorePayload.getPassword());
		keystoreService.save(secondaryNL);

		log.info("Loaded new keystore (" + secondaryNL.getSubjectDn() + ") with alias " + secondaryNL.getAlias());

		if (secondaryOCES == null) {
			secondaryOCES = new Keystore();
			secondaryOCES.setAlias(KnownCertificateAliases.OCES_SECONDARY.toString());
		}
		
		// clear existing data
		secondaryOCES.setDisabled(false);
		secondaryOCES.setKeystore(null);
		secondaryOCES.setPassword(null);
		secondaryOCES.setLastUpdated(LocalDateTime.now());

		// load new data
		getKeystoreFromPfx(secondaryOCES, keystorePayload.getKeystore(), keystorePayload.getPassword());
		keystoreService.save(secondaryOCES);
		
		log.info("Loaded new keystore (" + secondaryOCES.getSubjectDn() + ") with alias " + secondaryOCES.getAlias());
		
		certificateChangelogService.newCertificate(operatorId, "Imported " + secondaryOCES.getSubjectDn());

		// plan rollover
		settingService.setLocalDateTimeSetting(SettingKey.CERTIFICATE_ROLLOVER_NL_TTS, keystorePayload.getRolloverTts());
		settingService.setLocalDateTimeSetting(SettingKey.CERTIFICATE_ROLLOVER_TTS, keystorePayload.getRolloverTts());
		
		return ResponseEntity.ok().build();
	}

	private void getKeystoreFromPfx(Keystore keystore, String payload, String password) throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");

		byte[] content = Base64.getDecoder().decode(payload);
		ks.load(new ByteArrayInputStream(content), password.toCharArray());
		
		String alias = ks.aliases().nextElement();
		X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);
		String subject = certificate.getSubjectX500Principal().getName();
		subject = prettyPrint(subject);
		LocalDate expires = certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		keystore.setKeystore(content);
		keystore.setPassword(password);
		keystore.setExpires(expires);
		keystore.setSubjectDn(subject);
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
