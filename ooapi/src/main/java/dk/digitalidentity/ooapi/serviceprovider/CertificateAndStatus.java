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
package dk.digitalidentity.ooapi.serviceprovider;

import dk.digitalidentity.ooapi.certificate.*;

/**
 * Wrapper class that holds an <code>OcesCertificate</code> and its status.
 */
public class CertificateAndStatus {
	private CertificateStatus certificateStatus;
	private OcesCertificate certificate;
	private String rememberMyUserIdToken;


	/**
	 * Contructor
	 * @param certificate encapsulated certificate
	 * @param certificateStatus encapsulated status 
	 */
	public CertificateAndStatus(OcesCertificate certificate,
			CertificateStatus certificateStatus) {
		this.certificate = certificate;
		this.certificateStatus = certificateStatus;
	}
	
	/*
	 * Constructor
	 * @param certificate encapsulated certificate
	 * @param certificateStatus encapsulated status 
	 * @param token rememberMyUserId token 
	 */
	public CertificateAndStatus(OcesCertificate certificate,
			CertificateStatus certificateStatus, String token) {
		this(certificate, certificateStatus);
		this.rememberMyUserIdToken = token;
	}

	/**
	 * Gets the encapsulated certificate status @see CertificateStatus
	 * @return the encapsulated certificate status @see CertificateStatus
	 */
	public CertificateStatus getCertificateStatus() {
		return certificateStatus;
	}

	/**
	 * Gets the encapsulated certificate
	 * @return the encapsulated certificate
	 */
	public OcesCertificate getCertificate() {
		return certificate;
	}

    public boolean isPoces(){
        return certificate instanceof PocesCertificate;
    }
	
	@Override
	public String toString() {
		return "Status " + certificateStatus.toString() + " for certifikat " + certificate;
	}

	public String getRememberMyUserIdToken() {
		return rememberMyUserIdToken;
	}

}