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

import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Certificate Authority (CA). This class can either model a root CA or an
 * issuing CA. If it is a root CA the signing CA will be null
 * and <code>isRoot()</code> will return true.
 */
@SuppressWarnings("serial")
public class CA implements CertificateChainElement, Serializable {

	private X509Certificate certificate;
	private CA issuingCa;

	/**
	 * Constructs a CA with <code>certificate</code> as the certificate of this
	 * CA and <code>issuingCa</code> as the parent CA which has signed the
	 * certificate of this CA
	 * 
	 * @param certificate
	 *            CA certificate
	 * @param issuingCa
	 *            CA which has signed the certificate of this CA
	 */
	public CA(X509Certificate certificate, CA issuingCa) {
		if (certificate == null) {
			throw new IllegalArgumentException("certificate cannot be null");
		}
		this.certificate = certificate;
		this.issuingCa = issuingCa;
	}

	/**
	 * Gets the certificate of this CA
	 * 
	 * @return certificate of this CA
	 */
	// FIXME burde denne ikke hedde export certificate og klone certifikatet?
	public X509Certificate getCertificate() {
		return certificate;
	}

	/**
	 * Gets the CA which has signed the certificate of this CA
	 * 
	 * @return CA which has signed the certificate of this CA
	 */
	public CA getSigningCA() {
		return issuingCa;
	}

	/**
	 * Returns <code>true></code> if this CA is a root CA otherwise false
	 */
	public boolean isRoot() {
		if (issuingCa != null) {
			return false;
		}
		try {
			certificate.verify(certificate.getPublicKey());
			return true;
		} catch (InvalidKeyException e) {
			return false;
		} catch (CertificateException e) {
			return false;
		} catch (NoSuchAlgorithmException e) {
			return false;
		} catch (NoSuchProviderException e) {
			return false;
		} catch (SignatureException e) {
			return false;
		}
	}

	/**
	 * Gets the public key of this CA
	 * 
	 * @return public key of this CA
	 */
	public PublicKey getPublicKey() {
		return certificate.getPublicKey();
	}

    @Override
	public String toString() {
		return certificate.getSubjectDN().toString();
	}
}
