/*
    Copyright 2010 Nets DanID

    This file is part of OpenOcesAPI.

    OpenOcesAPI is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.

    OpenOcesAPI is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with OpenOcesAPI; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


    Note to developers:
    If you add code to this file, please take a minute to add an additional
    @author statement below.
*/
package dk.digitalidentity.ooapi.certificate;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.jce.X509Principal;

import dk.digitalidentity.ooapi.config.OOAPIConfiguration;
import dk.digitalidentity.ooapi.environment.Environments;
import dk.digitalidentity.ooapi.environment.RootCertificates;
import dk.digitalidentity.ooapi.exceptions.InvalidCaIssuerUrlException;
import dk.digitalidentity.ooapi.exceptions.NonOcesCertificateException;
import dk.digitalidentity.ooapi.exceptions.TrustCouldNotBeVerifiedException;
import dk.digitalidentity.ooapi.ldap.LDAPFactory;
import dk.digitalidentity.ooapi.serviceprovider.ServiceProviderSetup;
import dk.digitalidentity.ooapi.utils.HttpClient;
import dk.digitalidentity.ooapi.utils.X509CertificatePropertyExtrator;
import dk.digitalidentity.ooapi.validation.LdapCrlDownloader;
import dk.digitalidentity.ooapi.validation.PartitionedCrlRevocationChecker;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory able to create an <code>OcesCertificate</code>.
 */
@SuppressWarnings("deprecation")
@Slf4j
public class OcesCertificateFactory {
	private static final OcesCertificateFactory instance = new OcesCertificateFactory();

	private OcesCertificateFactory() {
	}

	public static OcesCertificateFactory getInstance() {
		return instance;
	}

	/**
	 * Generates an <code>OcesCertificate</code>. The returned
	 * <code>OcesCertificate</code> is the end user certificate, which has a
	 * parent relation to the certificate of its issuing CA which again can have
	 * a parent relation to the certificate of the root CA. The root CA has no
	 * parent relation.
	 * 
	 * The factory verifies that each certificate in the certificate chain has
	 * been signed by its issuing CA.
	 * 
	 * @param certificates
	 *            List of certificates to create OcesCertificate chain from.
	 * @return <code>OcesCertificate</code> with parent relation to (chain of)
	 *         issuing CAs. Depending on the Subject DN in the certificate a
	 *         <code>PocesCertificate</code>, <code>MocesCertificate</code>,
	 *         <code>VocesCertificate</code>, or <code>FocesCertificate</code>
	 *         will be created.
	 * @throws TrustCouldNotBeVerifiedException
	 *             when an OcesCertificate in the chain cannot be trusted, i.e.
	 *             has not been signed by its issuing CA.
	 */
	public OcesCertificate generate(List<X509Certificate> certificates) throws TrustCouldNotBeVerifiedException {
		certificates = sortCertificatesIssuerLast(certificates);
		addIssuerCertificateIfNeeded(certificates);
		validateExactlyOneChainInList(certificates);
		appendRootIfMissing(certificates);
		CA signingCa = createCaChain(certificates);
		OcesCertificate certificate = selectCertificateSubclass(signingCa, certificates.get(0));
		if (ChainVerifier.verifyTrust(certificate)) {
			return certificate;
		}
		throw new TrustCouldNotBeVerifiedException(certificate, Environments.getTrustedEnvironments());
	}

	protected void addIssuerCertificateIfNeeded(List<X509Certificate> certificates) {
		log.debug("adding issuer certificate if needed");
		if (certificates.size() == 1) {
			log.debug("Certificates size is 1");
			X509Certificate certificate = certificates.get(0);
			X509Certificate caCert = null;

			if (certificate.getIssuerDN().getName().toUpperCase().indexOf("TRUST2408") != -1) {
				log.debug("Certificate issuer is Trust 2408");

				String url = null;
				try {
					url = X509CertificatePropertyExtrator.getCaIssuerUrl(certificate);
					log.debug("Certificate CA issuer URL is: " + url);

					caCert = HttpClient.downloadCertificate(url);
					if (caCert != null) {
						log.debug("CA certificate retrieved");
					}
					certificates.add(caCert);
				}
				catch (InvalidCaIssuerUrlException e) {
					log.debug("Invalid CA issuer url, retrieving by DN config name");
					if (ServiceProviderSetup.getCurrentChecker() instanceof PartitionedCrlRevocationChecker) {
						caCert = getICaCertFromLdap(certificate);
						if (caCert != null) {
							log.debug("CA certificate retrieved");
						}
					}
					certificates.add(caCert);
				}
				catch (IllegalStateException ise) {
					log.debug("Unable to retrieve CA issuer URL " + url + " : ", ise);
				}
			}
		}
	}

	private X509Certificate getICaCertFromLdap(X509Certificate cert) {
		// fetch CA certificate based on service providers revocationlist access
		// choice LDAP|HTTP
		String ldapHostName = LDAPFactory.getLdapHostNamefromCaDN(cert.getIssuerDN().getName());
		log.debug("Retrieving CA from LDAP host with parameters(ldapthostname,issuerDNName)  " + ldapHostName + "," + cert.getIssuerDN().getName());
		X509Certificate caCert = new LdapCrlDownloader().downloadCertificate(ldapHostName, cert.getIssuerDN().getName());
		return caCert;
	}

	private void validateExactlyOneChainInList(List<X509Certificate> certificates) {
		if (certificates.isEmpty()) {
			throw new NonOcesCertificateException("Only self-signed certificates found");
		}
		for (int i = 0; i < certificates.size() - 1; i++) {
			X500Principal issuer = certificates.get(i).getIssuerX500Principal();
			X500Principal nextSubject = certificates.get(i + 1).getSubjectX500Principal();
			if (!issuer.equals(nextSubject)) {
				throw new IllegalStateException("certificate list holds something that is not a certificate chain");
			}
		}
	}

	/**
	 * Find all certificates that are not self-signed. Then sort all
	 * certificates, so that issuers are after the certificates they sign.
	 * Certificates in the list that were not part of the trust chain for the
	 * digital signatures are not retained.
	 *
	 * @param inputCertificates
	 * @return sorted certificates needed to verify the digital signatures from
	 *         the input list
	 */
	private List<X509Certificate> sortCertificatesIssuerLast(List<X509Certificate> inputCertificates) {
		List<X509Certificate> result = new ArrayList<X509Certificate>(inputCertificates.size());
		Map<X500Principal, X509Certificate> certBySubject = new HashMap<X500Principal, X509Certificate>();
		for (X509Certificate certificate : inputCertificates) {
			certBySubject.put(certificate.getSubjectX500Principal(), certificate);
			boolean[] keyUsage = certificate.getKeyUsage();
			if (keyUsage != null && !keyUsage[6]) {
				result.add(certificate);
			}
		}
		for (int i = 0; i < result.size(); i++) {
			X509Certificate certificate = result.get(i);
			X509Certificate issuer = certBySubject.get(certificate.getIssuerX500Principal());
			if (issuer != null && !result.contains(issuer)) {
				result.add(issuer);
			}
		}
		return result;
	}

	private OcesCertificate selectCertificateSubclass(CA signingCa, X509Certificate endUserCertificate) {
		String subjectSerialNumber = extractSubjectSerialNumber(endUserCertificate);

		Environments.Environment currentEnv = getEnvironmentForRoot(signingCa);
		log.debug("Current environment is: " + currentEnv + " for signing CA: " + signingCa.toString());

		OcesCertificate certificate;
		if (subjectSerialNumber.startsWith("PID:") && matchPocesPolicy(endUserCertificate, currentEnv)) {
			certificate = new PocesCertificate(endUserCertificate, signingCa);
		}
		else {
			throw new NonOcesCertificateException("End user certificate is not POCES, dn is " + endUserCertificate.getSubjectX500Principal().getName(X500Principal.RFC1779));
		}

		return certificate;
	}

	private boolean matchPocesPolicy(X509Certificate endUserCertificate, Environments.Environment currentEnv) {
		if (Environments.Environment.OCESII_DANID_ENV_PREPROD.equals(currentEnv)) {
			return true; // we do not validate OCES2 preprod as external
							// partners might have older certificates not
							// satisfying this.
		}
		final String propertyName = "poces.policies.prefix.danid." + currentEnv;
		final String propertyValue = OOAPIConfiguration.getInstance().getProperty(propertyName);
		if (propertyValue == null) {
			log.debug("The property " + propertyName + " was not found.");
		}
		return matchPolicy(endUserCertificate, propertyValue);
	}

	private boolean matchPolicy(X509Certificate endUserCertificate, String oidPrefix) {
		return X509CertificatePropertyExtrator.getCertificatePolicyOID(endUserCertificate).startsWith(oidPrefix);
	}

	private Environments.Environment getEnvironmentForRoot(CA ca) {
		if (!ca.isRoot()) {
			return getEnvironmentForRoot(ca.getSigningCA());
		}
		return RootCertificates.getEnvironment(ca);
	}

	@SuppressWarnings({ "rawtypes" })
	private String extractSubjectSerialNumber(X509Certificate endUserCertificate) {
		X509Principal x509Principal;
		try {
			x509Principal = new X509Principal(endUserCertificate.getSubjectX500Principal().getEncoded());
		}
		catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
		Vector snValues = x509Principal.getValues(X509Principal.SN);
		if (snValues.size() != 1) {
			throw new IllegalArgumentException("Missing unique SSN in dn: " + endUserCertificate.getSubjectX500Principal().getName(X500Principal.RFC1779));
		}
		String ssn = (String) snValues.get(0);
		return ssn;
	}

	/**
	 * Requires the input certificates to be sorted so that the parent is right
	 * after every certificate.
	 * 
	 * @param certificates
	 * @return
	 */
	private CA createCaChain(List<X509Certificate> certificates) {
		CA parent = null;
		for (int i = certificates.size() - 1; i > 0; i--) {
			parent = new CA(certificates.get(i), parent);
		}
		return parent;
	}

	private void appendRootIfMissing(List<X509Certificate> certificates) {
		X509Certificate last = certificates.get(certificates.size() - 1);
		if (!isSelfSigned(last)) {
			X509Certificate certificateBySubjectDn = RootCertificates.lookupCertificateBySubjectDn(last.getIssuerX500Principal());
			certificates.add(certificateBySubjectDn);
		}
	}

	private boolean isSelfSigned(X509Certificate certificate) {
		try {
			certificate.verify(certificate.getPublicKey());
			return true;
		}
		catch (InvalidKeyException e) {
			log.trace("Problem verifying signature", e);
		}
		catch (CertificateException e) {
			log.trace("Problem verifying signature", e);
		}
		catch (NoSuchAlgorithmException e) {
			log.trace("Problem verifying signature", e);
		}
		catch (NoSuchProviderException e) {
			log.trace("Problem verifying signature", e);
		}
		catch (SignatureException e) {
			log.trace("Problem verifying signature", e);
		}
		return false;
	}
}
