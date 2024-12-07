package dk.digitalidentity.api;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
import dk.digitalidentity.common.service.CertificateChangelogService;
import dk.digitalidentity.service.KeystoreService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CertManagerApi {

	@Autowired
	private KeystoreService keystoreService;
	
	@Autowired
	private CertificateChangelogService certificateChangelogService;

	@GetMapping("/api/certmanager/all")
	@ResponseBody
	public ResponseEntity<?> getAll() {
		List<Keystore> keystores = keystoreService.findAll();
		
		return ResponseEntity.ok(keystores.stream().filter(k -> !k.isDisabled()).map(k -> new KeystoreInfo(k)).collect(Collectors.toList()));
	}

	@PutMapping("/api/certmanager/disable/{alias}")
	@ResponseBody
	public ResponseEntity<?> disableCertificate(@RequestParam String operatorId, @PathVariable("alias") String alias) {
		Keystore keystore = keystoreService.findByAlias(alias);
		if (keystore == null) {
			log.warn("No certificate found with alias: " + alias);
			return ResponseEntity.notFound().build();
		}

		log.info("Disabled " + keystore.getSubjectDn());
		certificateChangelogService.deleteCertificate(operatorId, "Deleted " + keystore.getSubjectDn());

		keystore.setDisabled(true);
		keystoreService.save(keystore);

		return ResponseEntity.ok().build();
	}

	@PostMapping("/api/certmanager/new")
	@ResponseBody
	public ResponseEntity<?> loadCertificate(@RequestParam(name = "operatorId") String operatorId, @RequestParam(required = false, defaultValue = "false", name = "force") boolean force, @RequestBody KeystorePayload keystorePayload) throws Exception {
		Keystore keystore = keystoreService.findByAlias(keystorePayload.getAlias());
		if (keystore != null && !force) {
			return ResponseEntity.badRequest().body("Keystore with alias " + keystorePayload.getAlias() + " already exists - set force=true to force overwriting it");
		}

		if (keystore == null) {
			keystore = new Keystore();
		}
		
		// clear settings
		keystore.setAlias(keystorePayload.getAlias());
		keystore.setDisabled(false);
		keystore.setKms(false);
		keystore.setKmsAlias(null);
		keystore.setKeystore(null);
		keystore.setPassword(null);
		keystore.setLastUpdated(LocalDateTime.now());

		if (StringUtils.hasLength(keystorePayload.getCertificate()) && StringUtils.hasLength(keystorePayload.getKmsAlias())) {
			getKeystoreFromKms(keystore, keystorePayload.getCertificate(), keystorePayload.getKmsAlias());
		}
		else if (StringUtils.hasLength(keystorePayload.getKeystore()) && StringUtils.hasLength(keystorePayload.getPassword())) {
			getKeystoreFromPfx(keystore, keystorePayload.getKeystore(), keystorePayload.getPassword());
		}
		else {
			return ResponseEntity.badRequest().body("Either certificate/kmsAlias has to be part of the payload, or keystore/password has to be part of the payload");
		}

		keystoreService.save(keystore);
		
		log.info("Loaded new keystore (" + keystore.getSubjectDn() + ") with alias " + keystore.getAlias());
		
		certificateChangelogService.newCertificate(operatorId, "Imported " + keystore.getSubjectDn());

		return ResponseEntity.ok().build();
	}
	
	private void getKeystoreFromKms(Keystore keystore, String certificate, String kmsAlias) throws Exception {
		// do this to make sure it is a valid certificate before storing in DB
		CertificateFactory factory = CertificateFactory.getInstance("X.509");
		X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(certificate)));
		byte[] encoded = cert.getEncoded();
		
		// extract certificate information
		String subject = cert.getSubjectX500Principal().getName();
		subject = prettyPrint(subject);
		LocalDate expires = cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		keystore.setCertificate(encoded);
		keystore.setExpires(expires);
		keystore.setSubjectDn(subject);
		keystore.setKms(true);
		keystore.setKmsAlias(kmsAlias);
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
