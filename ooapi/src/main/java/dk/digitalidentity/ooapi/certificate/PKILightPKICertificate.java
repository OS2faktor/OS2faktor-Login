package dk.digitalidentity.ooapi.certificate;

import dk.digitalidentity.ooapi.exceptions.NonOcesCertificateException;
import dk.digitalidentity.ooapi.utils.X509CertificatePropertyExtrator;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PKILightPKICertificate implements Certificate {

	private static final long serialVersionUID = -1038427028480385039L;
	private static final Pattern subjectSerialNumberRegExp = Pattern.compile("^CVR:(\\d{8})\\-UID:(.+){1,47}$");
    protected X509Certificate certificate;
    private CA issuingCA;

    /**
     * Creates a PKILightCertificate.
     *
     * @param certificate <code>X509Certificate</code> to encapsulate
     * @param issuingCa   parent relation to its issuing CA
     */
    protected PKILightPKICertificate(X509Certificate certificate, CA issuingCa) {
        this.certificate = certificate;
        this.setIssuingCa(issuingCa);
    }

	public void setIssuingCa(CA issuingCA) {
		this.issuingCA = issuingCA;
	}

	public CA getSigningCA() {
		return issuingCA;
	}

    /**
     * Gets the status of the certificate @see <code>CertificateStatus</code>
     *
     * @return the status of the certificate @see <code>CertificateStatus</code>
     */
    public CertificateStatus validityStatus() {
        return validityStatus(new Date());
    }

    /**
     * Checks if the certificate is valid on the given date.
     *
     * @param date Date for validity check.
     * @return <code>CertificateStatus.VALID</code> if the certificate is valid,
     *         <code>CertificateStatus.EXPIRED</code> if certificate is expired, or
     *         <code>CertificateStatus.NOT_YET_VALID if the certificate is not yet valid on the given date</code>
     */
    public CertificateStatus validityStatus(Date date) {
        try {
            certificate.checkValidity(date);
            return CertificateStatus.VALID;
        } catch (CertificateExpiredException e) {
            return CertificateStatus.EXPIRED;
        } catch (CertificateNotYetValidException e) {
            return CertificateStatus.NOT_YET_VALID;
        }
    }

    /**
     * Gets the subject distinguished name of this certificate.
     *
     * @return the subject distinguished name of this certificate.
     */
    public String getSubjectDistinguishedName() {
        return getSubjectDistinguishedName(X500Principal.RFC2253);
    }

    /**
     * Gets the subject distinguished name of this certificate.
     *
     * @param format The RFC format fx. RFC2253 or RFC1779
     * @return the subject distinguished name of this certificate.
     */
    public String getSubjectDistinguishedName(String format) {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("2.5.4.5", "serialnumber");
        return certificate.getSubjectX500Principal().getName(format, map);
    }

    /**
     * Gets the subject CN (common name) of the certificate.
     *
     * @return The subject CN (common name) of the certificate.
     */
    public String getSubjectCN() {
        return X509CertificatePropertyExtrator.getSubjectCommonName(certificate);
    }

     /**
     * Gets the email in this certificate or null if no email is part of this certificate.
     *
     * @return the email in this certificate or null if no email is part of this certificate.
     */
    public String getEmailAddress() {
        return X509CertificatePropertyExtrator.getEmailAddress(certificate);

    }

    /**
     * Gets the SubjectSerialNumber of the certificate
     *
     * @return The SubjectSerialNumber of the certificate
     */
    public String getSubjectSerialNumber() {
        return X509CertificatePropertyExtrator.getSubjectSerialNumber(certificate);
    }

    /**
	 * Gets the UID of the PKI Light certificate
	 *
	 * @return The UID of the PKI Light certificate
	 */
	public String getUid() throws NonOcesCertificateException {
		return extractIdFromSubjectSerialNumber(2);
	}

	/**
	 * Gets the CVR of the PKI Light certificate
	 *
	 * @return The CVR of the PKI Light certificate
	 */
	public String getCvr() throws NonOcesCertificateException {
		return extractIdFromSubjectSerialNumber(1);
	}

	private String extractIdFromSubjectSerialNumber(int regExpGroup) {
		String subjectSerialNumber = getSubjectSerialNumber();
		Matcher matcher = subjectSerialNumberRegExp.matcher(subjectSerialNumber);
        if (matcher.matches()) {
        	return matcher.group(regExpGroup);
        } else {
			throw new NonOcesCertificateException("No UID number present in subject serialnumber, subject serialnumber is " + subjectSerialNumber);
        }
	}

     /**
     * Gets a clone of the encapsulated <code>X509Certificate</code>
     *
     * @return A clone of the encapsulated <code>X509Certificate</code>
     */
    public X509Certificate exportCertificate() {
        //FIXME should this be clone of the certicate.
        return certificate;
    }

}
