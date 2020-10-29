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

import org.bouncycastle.asn1.x509.X509Name;

import dk.digitalidentity.ooapi.utils.X509CertificatePropertyExtrator;
import dk.digitalidentity.ooapi.validation.CRLDistributionPoints;
import dk.digitalidentity.ooapi.validation.CRLDistributionPointsExtractor;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Abstract super class for all types of OCES certificates.
 */
@SuppressWarnings({ "serial", "deprecation" })
public abstract class OcesCertificate implements Certificate, OcesCertificateFacade {

    protected X509Certificate certificate;
    private CA issuingCa;

    /**
     * Creates a OcesCertificate.
     *
     * @param certificate <code>X509Certificate</code> to encapsulate
     * @param issuingCa   parent relation to its issuing CA
     */
    protected OcesCertificate(X509Certificate certificate, CA issuingCa) {
        this.certificate = certificate;
        this.issuingCa = issuingCa;
    }

    /**
     * Gets the bytes of the encapsulated <code>X509Certificate</code>.
     * Encoding is dictated by the encoding of the encapsulated X509Certificate.
     *
     * @return The bytes of the encapsulated <code>X509Certificate</code>.
     *         Encoding is dictated by the encoding of the encapsulated X509Certificate.
     */
    public byte[] getBytes() {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Gets the subject DN (distinguished name) of the certificate.
     *
     * @return The subject DN (distinguished name) of the certificate.
     */
    protected X509Name getParsedSubjectDN() {
        return X509CertificatePropertyExtrator.getParsedSubjectDN(certificate);
    }

    /**
     * Gets a specific element of the subject DN
     *
     * @param element <code>Name</code> of element to return value of
     * @return Specific element of the subject DN
     */
    protected Object getElementInX509Name(String element) {
        return X509CertificatePropertyExtrator.getElementInX509Name(certificate, element);
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
     * Gets the SubjectSerialNumber of the certificate
     *
     * @return The SubjectSerialNumber of the certificate
     */
    public String getSubjectSerialNumber() {
        return X509CertificatePropertyExtrator.getSubjectSerialNumber(certificate);
    }

    /**
     * Gets the OCSP URL of the certificate
     *
     * @return The OCSP URL of the certificate
     */
    public String getOcspUrl() {
        return X509CertificatePropertyExtrator.getOcspUrl(certificate);
    }

    /**
     * Gets the caIssuer URL of the certificate
     *
     * @return The caIssuer URL of the certificate
     * @throws dk.digitalidentity.ooapi.exceptions.InvalidCaIssuerUrlException in case that no ca issuer url can be extracted from the certificate.
     */
    public String getCaIssuerUrl() {
        return X509CertificatePropertyExtrator.getCaIssuerUrl(certificate);
    }


    /**
     * Gets the start date of the validity period.
     *
     * @return the start date of the validity period.
     */
    public Date getNotBefore() {
        return certificate.getNotBefore();
    }

    /**
     * Gets the end date of the validity period.
     *
     * @return the end date of the validity period.
     */
    public Date getNotAfter() {
        return certificate.getNotAfter();
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
     * Returns true if the certificate is valid on the given date.
     *
     * @param date date to check certificate validity
     * @return true if this certificate is valid on the given date.
     */
    public boolean validOnDate(Date date) {
        return validityStatus(date) == CertificateStatus.VALID;
    }

    /**
     * Gets the serial number of certificate. The serial number is unique for all certificates issued by a specific CA. @see <code>java.security.cert.X509Certificate#getSerialNumber()</code>
     *
     * @return serial number of certificate. The serial number is unique for all certificates issued by a specific CA. @see <code>java.security.cert.X509Certificate#getSerialNumber()</code>
     */
    public BigInteger getSerialNumber() {
        return certificate.getSerialNumber();
    }

    /**
     * Gets the distinguished name of the issuer CA.
     *
     * @return distinguished name of the issuer CA.
     */
    public String getIssuerDn() {
        return certificate.getIssuerDN().getName();
    }

    /**
     * Gets the distinguished name of this certificate.
     *
     * @return the distinguished name of this certificate.
     */
    public String getDn() {
        return certificate.getSubjectX500Principal().getName();
    }

    /**
     * Gets the subject distinguished name of this certificate.
     *
     * @return the subject distinguished name of this certificate.
     */
    public String getSubjectDistinguishedName() {
        return getSubjectDistinguishedName(X500Principal.RFC2253);
    }
    
    public String getSubjectDistinguishedNameOces2() {
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
     * Gets the certificate chain of this certificate. The certificate chain consists of this certificate and one (or more) of its signing CAs. The chain ends with the root CA.
     *
     * @return the certificate chain of this certificate. The certificate chain consists of this certificate and one (or more) of its signing CAs. The chain ends with the root CA.
     */
    public List<X509Certificate> getCertificateChain() {
        List<X509Certificate> chain = new ArrayList<X509Certificate>();
        chain.add(certificate);

        CA parent = getSigningCA();
        while (parent != null) {
            chain.add(parent.getCertificate());
            parent = parent.getSigningCA();
        }
        return chain;
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
     * The distribution point of the Certificate Revocation List (CRL) that this certificate must be checked against for revocation
     *
     * @return Distribution point as a <code>String</code> of the Certificate Revocation List (CRL) that this certificate must be checked against for revocation
     */
    public String getCrlDistributionPoint() {
        return getDistributionPoints().getCrlDistributionPoint();
    }

    /**
     * The distribution point of the partitioned Certificate Revocation List (CRL) that this certificate must be checked against for revocation
     *
     * @return Distribution point of the partitioned Certificate Revocation List (CRL) that this certificate must be checked against for revocation
     */
    public String getPartitionedCrlDistributionPoint() {
        return getDistributionPoints().getPartitionedCRLDistributionPoint();
    }

    /**
     * The distribution point of the Certificate Revocation List (CRL) that this certificate must be checked against for revocation
     *
     * @return Distribution point as a <code>CRLDistributionPoints</code> instance of the Certificate Revocation List (CRL)
     *         that this certificate must be checked against for revocation
     */
    public CRLDistributionPoints getDistributionPoints() {
        return CRLDistributionPointsExtractor.extractCRLDistributionPoints(certificate);
    }

    /**
     * Gets the signing Certificate Authority (CA) parent relation of this certificate
     *
     * @return the signing Certificate Authority (CA) parent relation of this certificate.
     */
    public CA getSigningCA() {
        return issuingCa;
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

    /**
     * Gets index of <code>element</code> in the <code>X509Name name</code> or -1 of index cannot be found
     * @return index of <code>element</code> in the <code>X509Name name</code> or -1 of index cannot be found
	 */
	@SuppressWarnings({ "rawtypes" })
    protected int indexOfElementInX509Name(X509Name name, String element) {
		Vector oids = name.getOIDs();
		for(int i = 0; i < oids.size(); i++) {
			Object o = oids.elementAt(i);
			if (element.equals(o.toString())) {
				return i;
			}
		}         
		return -1;
	}
}
