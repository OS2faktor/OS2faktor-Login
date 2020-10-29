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

import java.security.cert.X509CRLEntry;


import dk.digitalidentity.ooapi.certificate.CA;
import dk.digitalidentity.ooapi.certificate.OcesCertificateFacade;
import dk.digitalidentity.ooapi.environment.RootCertificates;
import dk.digitalidentity.ooapi.environment.Environments.Environment;
import dk.digitalidentity.ooapi.exceptions.InvalidCrlException;
import lombok.extern.slf4j.Slf4j;

/**
 * <code>RevocationChecker</code> based on a partitioned CRL.
 */
@Slf4j
public class PartitionedCrlRevocationChecker implements RevocationChecker {
	private static PartitionedCrlRevocationChecker ourInstance = new PartitionedCrlRevocationChecker();
	private CachedLdapCrlDownloader crlDownloader;

	private PartitionedCrlRevocationChecker() {
		crlDownloader = new CachedLdapCrlDownloader();
	}

	/**
	 * Gives the <code>PartionedCrlRevocationChecker</code> singleton.
	 */
	public static PartitionedCrlRevocationChecker getInstance() {
		return ourInstance;
	}

	/**
	 * The partitioned CRL to check for revocation is retrieved using LDAP.
	 */
	public boolean isRevoked(OcesCertificateFacade certificate) {
		CRL crl = getCrlInstance(certificate);
		if(crl==null) {
			return false;
		}
		
		return crl.isRevoked(certificate) || isRevoked(certificate.getSigningCA());
	}

	private CRL getCrlInstance(OcesCertificateFacade certificate) {
		String ldapPath = certificate.getPartitionedCrlDistributionPoint();
		Environment environment = RootCertificates.getEnvironment(certificate.getSigningCA());
		CRL crl = null;
		try {
			crl = crlDownloader.download(environment, ldapPath);
		}catch(Exception e) {
			log.error("Could not connect to crldir.certifikat.dk", e);
			return null;
		}
		
		if (!crl.isPartial()) {
			throw new InvalidCrlException("Crl was downloaded successfully, but is not a partial CRL", ldapPath);
		}
		
		if (!crl.isCorrectPartialCrl(ldapPath)) {
			throw new InvalidCrlException("Crl was downloaded successfully, but is not the correct partitioned crl", ldapPath);
		}
		
		return crl;
	}

	public X509CRLEntry getRevocationDetails(OcesCertificateFacade certificate) {
		CRL crl = getCrlInstance(certificate);
		
		return crl.getRevocationDetails(certificate);
	}

	public boolean isRevoked(CA ca) {
		if (ca.isRoot()) {
			return false;
		}
		
		Environment environment = RootCertificates.getEnvironment(ca.getSigningCA());
		
		return downloadCrl(ca, environment).isRevoked(ca) || isRevoked(ca.getSigningCA());
	}

	private CRL downloadCrl(CA ca, Environment environment) {
		String crlDistributionPoint = CRLDistributionPointsExtractor.extractCRLDistributionPoints(ca.getCertificate()).getPartitionedCRLDistributionPoint();
		
		return crlDownloader.download(environment, crlDistributionPoint);
	}
}
