package dk.digitalidentity.task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.KeystoreDao;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.util.PasswordUtils;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SelfSignedKeystoreGenerator {

	@Autowired
	private SettingService settingService;
	
	@Autowired
	private KeystoreDao keystoreDao;

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		try {
			if (!settingService.getBoolean(SettingKey.SELFSIGNED_CERTIFICATE_GENERATED)) {
				Keystore selfsignedKeystore = generateSelfsigendCertificate();
				if (selfsignedKeystore != null) {
					keystoreDao.save(selfsignedKeystore);
	
					settingService.setBoolean(SettingKey.SELFSIGNED_CERTIFICATE_GENERATED, true);
				}
			}
		}
		catch (Exception ex) {
			log.error("Update of ServiceProviders from database task failed", ex);
		}
	}
	
	private Keystore generateSelfsigendCertificate() throws Exception {
		String randomPassword = PasswordUtils.getPassword(16);

		// generate keypair
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(3072, new SecureRandom());
		KeyPair pair = generator.generateKeyPair();

		// generate certificate
		X509Certificate certificate = null;
		try {
			certificate = (X509Certificate) selfSign(pair);
		}
		catch (Exception ex) {
			log.error("Failed to generate selfsigned certificate", ex);
			return null;
		}
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		try {
			KeyStore ks = KeyStore.getInstance("PKCS12");
			ks.load(null, null);
			X509Certificate[] chain = new X509Certificate[1];
			chain[0] = certificate;
			ks.setKeyEntry("selfsigned", pair.getPrivate(), randomPassword.toCharArray(), chain);
			ks.store(outputStream, randomPassword.toCharArray());
		}
		catch (Exception ex) {
			log.error("Failed to add privateKey and certificate to keystore", ex);
			return null;
		}

		Keystore keystore = new Keystore();
		keystore.setKeystore(outputStream.toByteArray());
		keystore.setLastUpdated(LocalDateTime.now());
		keystore.setPassword(randomPassword);
		keystore.setAlias(KnownCertificateAliases.SELFSIGNED.toString());
		keystore.setExpires(certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
		keystore.setSubjectDn(certificate.getSubjectX500Principal().getName());

		return keystore;
	}

	public static Certificate selfSign(KeyPair keyPair) throws OperatorCreationException, CertificateException, IOException {
		Provider bcProvider = new BouncyCastleProvider();
		long now = System.currentTimeMillis();
		Date startDate = new Date(now);

		X500Name dnName = new X500Name("cn=selfsigned");

		// Using the current timestamp as the certificate serial number
		BigInteger certSerialNumber = new BigInteger(Long.toString(now));

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(startDate);
		calendar.add(Calendar.YEAR, 30);

		Date endDate = calendar.getTime();

		// Use appropriate signature algorithm based on your keyPair algorithm.
		String signatureAlgorithm = "SHA256WithRSA";

		SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

		X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, subjectPublicKeyInfo);

		ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).setProvider(bcProvider).build(keyPair.getPrivate());

		X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);

		Certificate selfSignedCert = new JcaX509CertificateConverter().getCertificate(certificateHolder);

		return selfSignedCert;
	}
}
