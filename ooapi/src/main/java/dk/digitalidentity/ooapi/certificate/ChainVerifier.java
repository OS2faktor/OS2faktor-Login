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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Set;

import dk.digitalidentity.ooapi.environment.Environments;

public class ChainVerifier {
	
	public static boolean verifyTrust(OcesCertificate certificate) {
		return verifyTrust(certificate.exportCertificate(), certificate.getSigningCA());
	}
	
	public static boolean verifyTrust(X509Certificate certificate, CA signingCA) {
		if (verify(certificate, signingCA.getPublicKey())) {
			if (verifyChain(signingCA, 0)) {
				return verifyRoot(signingCA);
			}
		}
		return false;
	}
	
	private static boolean verifyChain(CA ca, int pathLength) {
		// Check path length
		if (ca.getCertificate().getBasicConstraints() < pathLength) {
			return false;
		}
		if (isSelfSigned(ca) && !ca.isRoot()) {
			return false;
		}
		if (ca.isRoot()) {
			return true;
		}
		if (ca.getSigningCA() == null) {
			return false;
		}
		CA signingCA = ca.getSigningCA();
		if (signingCA.getCertificate().getBasicConstraints() >= 0) {
			if (verify(ca.getCertificate(), signingCA.getPublicKey())) {
				return verifyChain(ca.getSigningCA(), ++pathLength);
			}
		} 
		return false;
	}
	
	private static boolean isSelfSigned(CA ca) {
		return verify(ca.getCertificate(), ca.getPublicKey());
	}
	
	private static boolean verifyRoot(CA ca) {
		if (ca.isRoot()) {
			Set<TrustAnchor> anchors = Environments.getTrustAnchors();
			for (TrustAnchor trustAnchor : anchors) {
				if (trustAnchor.getTrustedCert().equals(ca.getCertificate())) {
					return true;
				}
			}
			return false;
		}
		return verifyRoot(ca.getSigningCA());
	}
	
	private static boolean verify(X509Certificate certificate, PublicKey publicKey) {
		try {
			certificate.verify(publicKey);
			return true;
		} catch (InvalidKeyException e) {
			//ignore on purpose
		} catch (CertificateException e) {
			//ignore on purpose
		} catch (NoSuchAlgorithmException e) {
			//ignore on purpose
		} catch (NoSuchProviderException e) {
			//ignore on purpose
		} catch (SignatureException e) {
			//ignore on purpose
		}
		return false;
		
	}
}
