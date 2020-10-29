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
 */package dk.digitalidentity.ooapi.exceptions;

import java.security.cert.X509Certificate;
import java.util.Collection;

import dk.digitalidentity.ooapi.certificate.OcesCertificate;
import dk.digitalidentity.ooapi.environment.PKIEnvironment;
import dk.digitalidentity.ooapi.environment.Environments.Environment;

/**
 * Thrown when trust of the certificate in a chain cannot be verified
 */
@SuppressWarnings("serial")
public class TrustCouldNotBeVerifiedException extends Exception {
	private final OcesCertificate certificate;
	private final Collection<Environment> trustedEnvironments;
	private final Collection<PKIEnvironment> trustedPKIEnvironments;

	public TrustCouldNotBeVerifiedException(OcesCertificate certificate,
			Collection<Environment> trustedEnvironments) {
		this.certificate = certificate;
		this.trustedEnvironments = trustedEnvironments;
		this.trustedPKIEnvironments = null;
	}

	public TrustCouldNotBeVerifiedException(
			Collection<PKIEnvironment> trustedPKIEnvironments) {
		trustedEnvironments = null;
		certificate = null;
		this.trustedPKIEnvironments = trustedPKIEnvironments;
	}

	public OcesCertificate getCertificate() {
		return certificate;
	}

	public Collection<Environment> getTrustedEnvironments() {
		return trustedEnvironments;
	}

	public String toString() {
		String t = "Trusted PKI environments: [";
		if ( trustedPKIEnvironments != null ) {
			for ( PKIEnvironment env : trustedPKIEnvironments) {
				t += env.getClass().getName();
			}
		}
		t += "]";
		
		String s = t+" The chain: ";
		String d = "";
		if (certificate != null) {
			for (X509Certificate cert : certificate.getCertificateChain()) {
				s += d;
				s += cert.getSubjectDN();
				d = "->";
			}
		}
		s += " could not be verified in any of the current trusted environments: "
				+ trustedEnvironments;
        return s;
    }
}