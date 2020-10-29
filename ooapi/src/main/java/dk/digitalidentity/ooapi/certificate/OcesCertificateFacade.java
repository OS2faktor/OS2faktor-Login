package dk.digitalidentity.ooapi.certificate;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import dk.digitalidentity.ooapi.validation.CRLDistributionPoints;

public interface OcesCertificateFacade extends Certificate {

	/**
	 * Gets the bytes of the encapsulated <code>X509Certificate</code>.
	 * Encoding is dictated by the encoding of the encapsulated X509Certificate.
	 *
	 * @return The bytes of the encapsulated <code>X509Certificate</code>.
	 *         Encoding is dictated by the encoding of the encapsulated X509Certificate.
	 */
	byte[] getBytes();

	/**
	 * Gets the subject CN (common name) of the certificate.
	 *
	 * @return The subject CN (common name) of the certificate.
	 */
	String getSubjectCN();

	/**
	 * Gets the SubjectSerialNumber of the certificate
	 *
	 * @return The SubjectSerialNumber of the certificate
	 */
	String getSubjectSerialNumber();

	/**
	 * Gets the OCSP URL of the certificate
	 *
	 * @return The OCSP URL of the certificate
	 */
	String getOcspUrl();

	/**
	 * Gets the caIssuer URL of the certificate
	 *
	 * @return The caIssuer URL of the certificate
	 * @throws dk.digitalidentity.ooapi.exceptions.InvalidCaIssuerUrlException in case that no ca issuer url can be extracted from the certificate.
	 */
	String getCaIssuerUrl();

	/**
	 * Gets the start date of the validity period.
	 *
	 * @return the start date of the validity period.
	 */
	Date getNotBefore();

	/**
	 * Gets the end date of the validity period.
	 *
	 * @return the end date of the validity period.
	 */
	Date getNotAfter();

	/**
	 * Gets the status of the certificate @see <code>CertificateStatus</code>
	 *
	 * @return the status of the certificate @see <code>CertificateStatus</code>
	 */
	CertificateStatus validityStatus();

	/**
	 * Checks if the certificate is valid on the given date.
	 *
	 * @param date Date for validity check.
	 * @return <code>CertificateStatus.VALID</code> if the certificate is valid,
	 *         <code>CertificateStatus.EXPIRED</code> if certificate is expired, or
	 *         <code>CertificateStatus.NOT_YET_VALID if the certificate is not yet valid on the given date</code>
	 */
	CertificateStatus validityStatus(Date date);

	/**
	 * Returns true if the certificate is valid on the given date.
	 *
	 * @param date date to check certificate validity
	 * @return true if this certificate is valid on the given date.
	 */
	boolean validOnDate(Date date);

	/**
	 * Gets the serial number of certificate. The serial number is unique for all certificates issued by a specific CA. @see <code>java.security.cert.X509Certificate#getSerialNumber()</code>
	 *
	 * @return serial number of certificate. The serial number is unique for all certificates issued by a specific CA. @see <code>java.security.cert.X509Certificate#getSerialNumber()</code>
	 */
	BigInteger getSerialNumber();

	/**
	 * Gets the distinguished name of the issuer CA.
	 *
	 * @return distinguished name of the issuer CA.
	 */
	String getIssuerDn();

	/**
	 * Gets the distinguished name of this certificate.
	 *
	 * @return the distinguished name of this certificate.
	 */
	String getDn();

	/**
	 * Gets the subject distinguished name of this certificate.
	 *
	 * @return the subject distinguished name of this certificate.
	 */
	String getSubjectDistinguishedName();

	/**
	 * Gets the subject distinguished name of this certificate.
	 *
	 * @param format The RFC format fx. RFC2253 or RFC1779
	 * @return the subject distinguished name of this certificate.
	 */
	String getSubjectDistinguishedName(String format);

	/**
	 * Gets the certificate chain of this certificate. The certificate chain consists of this certificate and one (or more) of its signing CAs. The chain ends with the root CA.
	 *
	 * @return the certificate chain of this certificate. The certificate chain consists of this certificate and one (or more) of its signing CAs. The chain ends with the root CA.
	 */
	List<X509Certificate> getCertificateChain();

	/**
	 * Gets the email in this certificate or null if no email is part of this certificate.
	 *
	 * @return the email in this certificate or null if no email is part of this certificate.
	 */
	String getEmailAddress();

	/**
	 * The distribution point of the Certificate Revocation List (CRL) that this certificate must be checked against for revocation
	 *
	 * @return Distribution point as a <code>String</code> of the Certificate Revocation List (CRL) that this certificate must be checked against for revocation
	 */
	String getCrlDistributionPoint();

	/**
	 * The distribution point of the partitioned Certificate Revocation List (CRL) that this certificate must be checked against for revocation
	 *
	 * @return Distribution point of the partitioned Certificate Revocation List (CRL) that this certificate must be checked against for revocation
	 */
	String getPartitionedCrlDistributionPoint();

	/**
	 * The distribution point of the Certificate Revocation List (CRL) that this certificate must be checked against for revocation
	 *
	 * @return Distribution point as a <code>CRLDistributionPoints</code> instance of the Certificate Revocation List (CRL)
	 *         that this certificate must be checked against for revocation
	 */
	CRLDistributionPoints getDistributionPoints();

	/**
	 * Gets the signing Certificate Authority (CA) parent relation of this certificate
	 *
	 * @return the signing Certificate Authority (CA) parent relation of this certificate.
	 */
	CA getSigningCA();

	/**
	 * Gets a clone of the encapsulated <code>X509Certificate</code>
	 *
	 * @return A clone of the encapsulated <code>X509Certificate</code>
	 */
	X509Certificate exportCertificate();

}