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
package dk.digitalidentity.ooapi.validation;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import javax.security.auth.x500.X500Principal;

import dk.digitalidentity.ooapi.TimeService;
import dk.digitalidentity.ooapi.certificate.CA;
import dk.digitalidentity.ooapi.certificate.OcesCertificateFacade;
import dk.digitalidentity.ooapi.certificate.PKILightPKICertificate;
import dk.digitalidentity.ooapi.exceptions.CrlExpiredException;
import dk.digitalidentity.ooapi.exceptions.CrlNotYetValidException;
import dk.digitalidentity.ooapi.exceptions.InvalidCrlException;
import dk.digitalidentity.ooapi.exceptions.InvalidSignatureException;

/**
 * Models a Certificate Revocation List (CRL). Encapsulates an <code>X509CRL</code> CRL 
 */
public class CRL {
	private X509CRL crl;
  private static final String PARTIAL_DISTRIBUTION_POINT_OID = "2.5.29.28";
  private TimeService timeservice = new CurrentTimeTimeService();
  /**
	 * Constructs CRL
	 * @param crl to encapsulate
	 */
	public CRL(X509CRL crl) {
		this.crl = crl;
	}

	/**
	 * Returns <code>true</code> if the given certificate is revoked and false otherwise 
	 * including if this CRL has expired.
	 * 
	 * @param certificate certificate to check for revocation
	 * @return <code>true</code> if the given certificate is revoked and false otherwise
	 * including if this CRL has expired.
	 * @throws IllegalStateException if this CRL is not valid or is not signed by the certificate's issuing CA.
	 */
	public boolean isRevoked(OcesCertificateFacade certificate) {
        try {
            verifyCrl(certificate.getSigningCA().getPublicKey());
        } catch (SignatureException e) {
            throw new InvalidSignatureException("CRL Issued by" + crl.getIssuerDN().getName()
                    + " does not have valid signature by certificate's issuer certificate "
                    + certificate.getSigningCA().getCertificate().getSubjectDN().getName(), e);
        }

        return isRevoked(certificate.exportCertificate());
	}

    /**
	 * Returns <code>true</code> if the given certificate is revoked and false otherwise
	 * including if this CRL has expired.
	 *
	 * @param certificate certificate to check for revocation
	 * @return <code>true</code> if the given certificate is revoked and false otherwise
	 * including if this CRL has expired.
	 * @throws IllegalStateException if this CRL is not valid or is not signed by the certificate's issuing CA.
	 */
	public boolean isRevoked(PKILightPKICertificate certificate) {
        return isRevoked(certificate.exportCertificate());
	}

    boolean isRevoked(CA ca) {
        if (ca.isRoot()) {
            throw new IllegalArgumentException("Cannot check revocation for root CA");
        }

        try {
            verifyCrl(ca.getSigningCA().getPublicKey());
        } catch (SignatureException e) {
            throw new InvalidSignatureException("CRL Issued by" + crl.getIssuerDN().getName()
                    + " does not have valid signature by ca's issuer certificate "
                    + ca.getSigningCA().getCertificate().getSubjectDN().getName(), e);
        }

        return isRevoked(ca.getCertificate());
    }

    private void verifyCrl(PublicKey publicKey) throws SignatureException {
        try {
            crl.verify(publicKey);
        } catch (CRLException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isRevoked(X509Certificate certificate) {
        assertCrlCurrentlyValid();
        assertCrlIssuedByCertificateIssuer(certificate);

		return crl.isRevoked(certificate);
    }

    private void assertCrlIssuedByCertificateIssuer(X509Certificate certificate) {
        Principal crlIssuer = crl.getIssuerDN();
        Principal certIssuer = certificate.getIssuerDN();
        if (!crlIssuer.equals(certIssuer)) {
            throw new IllegalStateException("CRL is not issued by the certificate's issuing CA. CRL is issued by: " + crlIssuer + ", certificate is issued by: " + certIssuer);
        }
    }

    public X509CRLEntry getRevocationDetails(OcesCertificateFacade certificate) {
        return crl.getRevokedCertificate(certificate.exportCertificate());
    }

	public boolean isValid() {
		return !isCrlExpired();
	}

    public boolean isCrlExpired() {
        assertCrlNotBeforeValidity();
        try {
            assertCrlNotExpired();
        } catch (CrlExpiredException e) {
            return true;
        }


        return false;
    }

	private void assertCrlCurrentlyValid() {
        assertCrlNotExpired();
        assertCrlNotBeforeValidity();
    }

    private void assertCrlNotBeforeValidity() {
		if (timeservice.getTime().before(crl.getThisUpdate())) {
			throw new CrlNotYetValidException("CRL is not yet valid, crl is valid from " + crl.getThisUpdate());
		}
    }

    private void assertCrlNotExpired() {
        if (timeservice.getTime().after(crl.getNextUpdate())) {
            throw new CrlExpiredException("CRL is expired, crl is valid to " + crl.getNextUpdate());
        }
	}
	
	public Date getValidFrom() {
		return crl.getThisUpdate();
	}

    public Set<String> getRevokedCertificates(Date from) {
        Set<String> serials = new TreeSet<String>();
        final Set<X509CRLEntry> revokedCertificates = getRevocationDetails(from);
        for (X509CRLEntry crlEntry : revokedCertificates) {
            serials.add(crlEntry.getSerialNumber().toString(16));
        }
        return serials;
    }

    public Set<X509CRLEntry> getRevocationDetails(Date from) {
        Set<X509CRLEntry> filteredRevokedCertificates = new TreeSet<X509CRLEntry>();
        final Set<? extends X509CRLEntry> revokedCertificates = crl.getRevokedCertificates();
        if (revokedCertificates != null) {
            for (X509CRLEntry crlEntry : revokedCertificates) {
                Date revocationDate = crlEntry.getRevocationDate();
                if (!revocationDate.before(from)) {
                    filteredRevokedCertificates.add(crlEntry);
                }
            }
        }
        return filteredRevokedCertificates;
    }

	public Date getValidUntil() {
		return crl.getNextUpdate();
	}

	public boolean isPartial() {
		return crl.getExtensionValue(PARTIAL_DISTRIBUTION_POINT_OID) != null;
	}

	public boolean isCorrectPartialCrl(String crlLdapUrl) {
		byte[] distributionPoint = crl.getExtensionValue(PARTIAL_DISTRIBUTION_POINT_OID);
		if (distributionPoint == null) {
			return false;
		}
		String distributionPointInfo = new String(distributionPoint).toLowerCase();
		String partialCrlNumber = getCrlNumberFromPartitionCrlUrl(crlLdapUrl);
		return distributionPointInfo.contains(partialCrlNumber);
	}

	private String getCrlNumberFromPartitionCrlUrl(String crlLdapUrl) {
		String crlUrl = new X500Principal(crlLdapUrl).getName(X500Principal.CANONICAL);
		
		String[] crlUrlSplit = crlUrl.split(",");
		if(crlUrlSplit == null || crlUrlSplit.length < 1) throw new InvalidCrlException("the crl url is malformed", crlLdapUrl);
		String crlNumber = crlUrlSplit[0];
		if(crlNumber.length() < "cn=crl".length()) throw new InvalidCrlException("The DN is not of expected format.", crlLdapUrl);
		return crlNumber.substring("cn=".length());
	}

    /**
     * For testing purposes only. Allows tests to trick this class into believing the time is something else than current time
     */
    protected void setTimeservice(TimeService timeservice) {
        this.timeservice = timeservice;
    }

    @Override
    public String toString() {
        if (crl != null) {
            return "CRL, validFrom: " +  getValidFrom() + ", validUntil: " + getValidUntil() + ", isPartial: " + isPartial();
        } else {
            return "CRL (null)";
        }
    }

    private class CurrentTimeTimeService implements TimeService {
        public Date getTime() {
            return Calendar.getInstance().getTime();
        }
    }
}
