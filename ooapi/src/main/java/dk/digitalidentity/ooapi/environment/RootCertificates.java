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
package dk.digitalidentity.ooapi.environment;

import dk.digitalidentity.ooapi.certificate.CA;
import dk.digitalidentity.ooapi.environment.Environments.Environment;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Root certificates of the different Environments
 */
public class RootCertificates {
    private static final Map<Environment, X509Certificate> rootCertificates;

    static {
        Map<Environment, String> certificates = new HashMap<Environment, String>();

        //INTERN CA Settings
        String internCaUT = RootCertificatesReader.getRootCertificate("intern_ca_ut.pem");
        if (internCaUT != null) {
            certificates.put(Environment.INTERN_CA_UT, internCaUT);
        }
        
        String internCaIT = RootCertificatesReader.getRootCertificate("intern_ca_it.pem");
        if (internCaIT != null) {
            certificates.put(Environment.INTERN_CA_IT, internCaIT);
        }

        String internCaET = RootCertificatesReader.getRootCertificate("intern_ca_et.pem");
        if (internCaET != null) {
            certificates.put(Environment.INTERN_CA_ET, internCaET);
        }

        String internCaIG = RootCertificatesReader.getRootCertificate("intern_ca_ig.pem");
        if (internCaIG != null) {
            certificates.put(Environment.INTERN_CA_IG, internCaIG);
        }
        
        String internCaOT = RootCertificatesReader.getRootCertificate("intern_ca_ot.pem");
        if (internCaOT != null) {
            certificates.put(Environment.INTERN_CA_OT, internCaOT);
        }
        
        String internCaPP = RootCertificatesReader.getRootCertificate("intern_ca_pp.pem");
        if (internCaPP != null) {
            certificates.put(Environment.INTERN_CA_PP, internCaPP);
        }
        
        String internCaPR = RootCertificatesReader.getRootCertificate("intern_ca_pr.pem");
        if (internCaPR != null) {
            certificates.put(Environment.INTERN_CA_PR, internCaPR);
        }

        String internCaLO = RootCertificatesReader.getRootCertificate("intern_ca_lo.pem");
        if (internCaLO != null) {
            certificates.put(Environment.INTERN_CA_LO, internCaLO);
        }

        //OCES2 Settings
        String localhostTestingCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_localhost.pem");
        if (localhostTestingCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_LOCALHOST_TESTING, localhostTestingCertificateOcesII);
        }

        String localhostCACertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_localhostca.pem");
        if (localhostCACertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_LOCALHOST_CA, localhostCACertificateOcesII);
        }

        String developmentCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_development.pem");
        if (developmentCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_DEVELOPMENT, developmentCertificateOcesII);
        }

        String systemTest3_aka_developmentTestCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_developmenttest.pem");
        if (systemTest3_aka_developmentTestCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_DEVELOPMENTTEST, systemTest3_aka_developmentTestCertificateOcesII);
        }

        String operationsTestCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_operations.pem");
        if (operationsTestCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_OPERATIONSTEST, operationsTestCertificateOcesII);
        }

        String integrationTestCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_integrationtest.pem");
        if (integrationTestCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_IGTEST, integrationTestCertificateOcesII);
        }

        String internalTestCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_internaltest.pem");
        if (internalTestCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_INTERNALTEST, internalTestCertificateOcesII);
        }

        String externalTestCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_externaltest.pem");
        if (externalTestCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_EXTERNALTEST, externalTestCertificateOcesII);
        }

        String preproductionCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_preproduction.pem");
        if (preproductionCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_PREPROD, preproductionCertificateOcesII);
        }

        String productionCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_prod.pem");
        if (productionCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_PROD, productionCertificateOcesII);
        }

        String localocesCertificateOcesII = RootCertificatesReader.getRootCertificate("oces2_localoces.pem");
        if (localocesCertificateOcesII != null) {
            certificates.put(Environment.OCESII_DANID_ENV_LOCALOCES, localocesCertificateOcesII);
        }

        rootCertificates = new HashMap<Environment, X509Certificate>();
        for (Environment environment : certificates.keySet()) {
            try {
                rootCertificates.put(environment, generateCertificate(certificates.get(environment)));
            } catch (CertificateException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Gets root certificate of the given <code>Environment</code>
     * 
     * @param environment
     *            to lookup root certificate for
     * @return root certificate of the given <code>Environment</code>
     */
    public static X509Certificate lookupCertificate(Environment environment) {
        if (!rootCertificates.containsKey(environment)) {
            throw new IllegalStateException("No certificate for: " + environment);
        }
        return rootCertificates.get(environment);
    }

    public static X509Certificate lookupCertificateBySubjectDn(X500Principal subjectDn) {
        for (Entry<Environment, X509Certificate> entry : rootCertificates.entrySet()) {
            if (entry.getValue().getSubjectX500Principal().equals(subjectDn)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("No certificate for subjectDn: " + subjectDn.getName("CANONICAL"));
    }

    public static boolean hasCertificate(Environment environment) {
        return rootCertificates.containsKey(environment);
    }

    private static X509Certificate generateCertificate(String certificate) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificate.getBytes()));
    }

    /**
     * Gets <code>Environment</code> for given <code>CA</code>
     * 
     * @param ca
     *            to lookup <code>Environment</code> for
     * @return <code>Environment</code> for given <code>CA</code>
     */
    public static Environment getEnvironment(CA ca) {
        if (ca == null) {
            throw new IllegalArgumentException("Ca is null");
        }
        if (!ca.isRoot()) {
            return getEnvironment(ca.getSigningCA());
        }
        for (Entry<Environment, X509Certificate> e : rootCertificates.entrySet()) {
            if (e.getValue().equals(ca.getCertificate())) {
                return e.getKey();
            }
        }
        throw new IllegalStateException(ca + " is not a known root certificate");
    }
}