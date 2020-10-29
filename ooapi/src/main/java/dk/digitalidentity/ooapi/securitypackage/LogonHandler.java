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
package dk.digitalidentity.ooapi.securitypackage;

import dk.digitalidentity.ooapi.certificate.CertificateStatus;
import dk.digitalidentity.ooapi.certificate.OcesCertificate;
import dk.digitalidentity.ooapi.certificate.PocesCertificate;
import dk.digitalidentity.ooapi.exceptions.AppletException;
import dk.digitalidentity.ooapi.exceptions.InternalException;
import dk.digitalidentity.ooapi.exceptions.NonOcesCertificateException;
import dk.digitalidentity.ooapi.exceptions.NonOpensignSignatureException;
import dk.digitalidentity.ooapi.serviceprovider.CertificateAndStatus;
import dk.digitalidentity.ooapi.serviceprovider.ServiceProviderException;
import dk.digitalidentity.ooapi.serviceprovider.ServiceProviderSetup;
import dk.digitalidentity.ooapi.signatures.OpenlogonSignature;
import dk.digitalidentity.ooapi.signatures.OpensignAbstractSignature;
import dk.digitalidentity.ooapi.signatures.OpensignSignatureFactory;
import dk.digitalidentity.ooapi.signatures.SignatureProperty;
import dk.digitalidentity.ooapi.validation.ErrorCodeChecker;

/**
 * This class handles validation and extraction of person ID from the output data provided by the Open Logon applet.
 */
public class LogonHandler {
	
    /**
	 * Given the output data from the Open Logon applet, the person ID (pid) is extracted if the login data is valid.
	 * 
	 * @param loginData the output data from the Open Logon applet.
     * @param challenge expected signed part of loginData
  	 * @param logonto expected value of the signature parameter <code>logonto</code> for OCESI applet responses or
     * of the signature parameter <code>RequestIssuer</code> for OCESII applet responses. Can be set to <code>null</code>
     * if validation should not be performed (this is not recommended).
	 * @return the pid of the certificate that is used for logging in. Only valid pids are returned.
	 * @throws ServiceProviderException in case that no pid can be extracted from the data provided.
	 * @throws AppletException in case the applet returned an error code.
	 */
	public static PersonID validateAndExtractPID(String loginData, String challenge, String logonto) throws ServiceProviderException, AppletException {
		CertificateAndStatus certAndStatus = validateAndExtractCertificateAndStatus(loginData, challenge, logonto);
		if (certAndStatus.getCertificateStatus() == CertificateStatus.VALID) {
			PocesCertificate pocesCertificate = ((PocesCertificate) certAndStatus.getCertificate());
			return new PersonID(pocesCertificate.getPid());
		} 
		
		throw new NonOcesCertificateException("certificate is invalid. Status: " + certAndStatus.getCertificateStatus());
	}

	/**
	 * Given the output data from the Open Logon applet, the certificate is extracted if the login data is valid.<br />
	 * NB! The validity of the certificate is <em>NOT</em> checked 
	 * (i.e. it is not checked if the certificate is valid, invalid, revoked, not yet valid or expired) 
	 * 
	 * @param loginData the output data from the Open Logon applet.
     * @param challenge expected signed part of loginData
     * @param logonto expected value of the signature parameter <code>logonto</code> for OCESI applet responses or
     * of the signature parameter <code>RequestIssuer</code> for OCESII applet responses. Can be set to <code>null</code>
     * if validation should not be performed (this is not recommended).
	 * @return the certificate that is used for logging in.
	 * @throws ServiceProviderException in case that the signature of the logindata is invalid
	 * @throws AppletException in case the applet returned an error code.
	 */
	public static OcesCertificate validateSignatureAndExtractCertificate(String loginData, String challenge, String logonto) throws ServiceProviderException, AppletException {
		try {
			OpenlogonSignature signature = createOpenlogonSignature(loginData);
            validateSignatureParameters(challenge, logonto, signature);

			if (signature.verify()) {
				return signature.getSigningCertificate(); 
			} 

			throw new NonOcesCertificateException("the signature of the login data is invalid, data is " + loginData);
		} catch (InternalException e) {
			throw new ServiceProviderException(e);
		} catch (NonOpensignSignatureException e) {
			throw new ServiceProviderException("Invalid signature " + loginData, e);
		}
	}

    /**
	 * Given the output data from the Open Logon applet, the certificate extracted if the login data is valid. 
	 * The status of the certificate is checked and a the certificate, its status as well as any rememberMyUserId token
	 * is returned wrapped in a CertificateStatus instance.
	 * 
	 * @param loginData the output data from the Open Logon applet.
     * @param challenge expected signed part of loginData
     * @param logonto expected value of the signature parameter <code>logonto</code> for OCESI applet responses or
     * of the signature parameter <code>RequestIssuer</code> for OCESII applet responses. Can be set to <code>null</code>
     * if validation should not be performed (this is not recommended).
     * @return the certificate that is used for logging in and the status of this certificate (wrapped in a CertificateStatus instance)
	 * @throws ServiceProviderException in case that no certificate can be extracted from the data provided.
	 * @throws AppletException in case the applet returned an error code.
	 */
	public static CertificateAndStatus validateAndExtractCertificateAndStatus(String loginData, String challenge, String logonto)
		throws ServiceProviderException, AppletException {
		try {
			OpenlogonSignature signature = createOpenlogonSignature(loginData);
            validateSignatureParameters(challenge, logonto, signature);

			if (signature.verify()) {
				OcesCertificate certificate = signature.getSigningCertificate();
				CertificateStatus status = certificate.validityStatus();
				
				if (status == CertificateStatus.VALID && ServiceProviderSetup.getCurrentChecker().isRevoked(certificate)) {
					status = CertificateStatus.REVOKED;
				}
				SignatureProperty ruidProperty = signature.getSignatureProperties().get("rememberUseridToken");
				String token = (ruidProperty == null ? null : ruidProperty.getValue() );

				return new CertificateAndStatus(certificate, status, token);
			} 
			
			throw new NonOcesCertificateException("the signature of the login data is invalid. Data is " + loginData);
		} catch (NonOpensignSignatureException e) {
			throw new ServiceProviderException(e);
		} catch (InternalException e) {
			throw new ServiceProviderException(e);
		} 
	}

	private static OpenlogonSignature createOpenlogonSignature(String loginData) throws NonOpensignSignatureException, InternalException, AppletException {
		if (ErrorCodeChecker.isError(loginData)) {
			throw new AppletException(ErrorCodeChecker.extractError(loginData));
		}
		OpensignAbstractSignature abstractSignature = OpensignSignatureFactory.getInstance().generateOpensignSignature(loginData);
		if (!(abstractSignature instanceof OpenlogonSignature)) {
			throw new IllegalArgumentException("argument of type " + abstractSignature.getClass() + " is not valid output from the logon applet");
		}
		return (OpenlogonSignature) abstractSignature;
	}

    private static void validateSignatureParameters(String challenge, String logonto, OpenlogonSignature signature) throws InternalException, ServiceProviderException {
        validateChallenge(challenge, signature);
        if (logonto != null) {
            validateLogonTo(signature, logonto);
        }
    }

    private static void validateChallenge(String challenge, OpenlogonSignature signature) throws InternalException {
        ChallengeVerifier.verifyChallenge(signature, challenge);
    }

    private static void validateLogonTo(OpenlogonSignature signature, String logonto) throws ServiceProviderException, InternalException {
        SignatureProperty logontoProperty = signature.getSignatureProperties().get("logonto");
        SignatureProperty requestIssuerProperty = signature.getSignatureProperties().get("RequestIssuer");

        if (logontoProperty != null && requestIssuerProperty != null) {
            throw new IllegalStateException("Invalid signature logonto and RequestIssuer parameters cannot both be set");
        }

        if (logontoProperty == null && requestIssuerProperty == null) {
            throw new IllegalStateException("Invalid signature either logonto or RequestIssuer parameters must be set");
        }

        if (logontoProperty != null) {
            String logontoPropertyValue = logontoProperty.getValue();
            verifyLogontoOrRequestIssuer(logontoPropertyValue, logonto);
        }

        if (requestIssuerProperty != null) {
            String requestIssuerValue = requestIssuerProperty.getValue();
            verifyLogontoOrRequestIssuer(requestIssuerValue, logonto);
        }
    }

    private static void verifyLogontoOrRequestIssuer(String logontoPropertyValue, String logonto) throws ServiceProviderException {
        boolean logontoVerified = false;
        for(String validLogonto : extractValidLogontos(logonto)) {
            if (logontoPropertyValue.equals(validLogonto)) {
                logontoVerified = true;
            }
        }
        if(!logontoVerified) {
            throw new ServiceProviderException("Invalid signature logonto or RequestIssuer parameter does not match expected value. Expected: "
                    + logonto + " actual: " + logontoPropertyValue);
        }
    }

    public static String[] extractValidLogontos(String logonto) {
        return logonto.split(";");
    }

}
