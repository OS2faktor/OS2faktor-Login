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

import dk.digitalidentity.ooapi.config.OOAPIConfiguration;
import dk.digitalidentity.ooapi.exceptions.InvalidEnvironmentException;
import lombok.extern.slf4j.Slf4j;

import java.security.cert.TrustAnchor;
import java.util.*;

/**
 * Defines the supported OCESI and OCESII test and production environments
 */
@Slf4j
public class Environments {

    public static enum Environment {
        // INTERNE CA'er
        INTERN_CA_UT,
        INTERN_CA_IT,
        INTERN_CA_ET,
        INTERN_CA_IG,
        INTERN_CA_OT,
        INTERN_CA_PP,
        INTERN_CA_PR,
        INTERN_CA_LO,
        // OCES II environments
        OCESII_DANID_ENV_PREPROD,
        OCESII_DANID_ENV_PROD,
        OCESII_DANID_ENV_EXTERNALTEST,
        OCESII_DANID_ENV_INTERNALTEST,
        OCESII_DANID_ENV_IGTEST,
        OCESII_DANID_ENV_OPERATIONSTEST,
        OCESII_DANID_ENV_DEVELOPMENTTEST,
        OCESII_DANID_ENV_DEVELOPMENT,
        OCESII_DANID_ENV_LOCALHOST_TESTING,
        OCESII_DANID_ENV_LOCALHOST_CA,
        OCESII_DANID_ENV_LOCALOCES
    }

    protected static boolean hasBeenSet = false;
    protected static Set<Environment> trustedEnvironments = new HashSet<Environment>(Arrays.asList(Environment.OCESII_DANID_ENV_PROD));

    /**
     * Sets the environments that must be supported in this execution context.
     * The list of environments that must be supported can only be set once in a specific execution context.
     *
     * @param environments varargs array of environments to support by this execution context.
     * @throws IllegalStateException if this method is called more than once
     */
    public synchronized static void setEnvironments(Environment... environments) {
        if (hasBeenSet) {
            throw new InvalidEnvironmentException("Environments cannot be set twice.");
        }
        if (environments != null && environments.length == 0) {
            log.warn("No environments are trusted. This can cause all sorts of problems.");
        }
        
        //Map between internal CA property names and OCES2 env properties, if mapping defined in property file
        for (Environment e : environments.clone()) {
            environments = addInternalEnvironment(e, environments);
        }
        
        for (Environment e : environments) {
            if (!RootCertificates.hasCertificate(e)) {
                throw new InvalidEnvironmentException("No root certificate for environment: " + e);
            }
        }
        int numberOfProductionEnvironments = countNumberOfProductionEnvironments(environments);
        if (numberOfProductionEnvironments > 0 && numberOfProductionEnvironments != environments.length) {
            throw new InvalidEnvironmentException("Production environments cannot be mixed with test environments.");
        }

        hasBeenSet = true;
        trustedEnvironments = new TreeSet<Environment>(Arrays.asList(environments));
    }

    private static int countNumberOfProductionEnvironments(Environment... environments) {
        int numberOfProductionEnvironments = 0;
        for (Environment e : environments) {
            if (Environment.OCESII_DANID_ENV_PROD.equals(e)
                    || Environment.INTERN_CA_PR.equals(e)) {
                numberOfProductionEnvironments++;
            }
        }
        return numberOfProductionEnvironments;
    }

    /**
     * Gets list of <code>X509Certificate</code>s of the CAs that are currently trusted.
     */
    public static Set<TrustAnchor> getTrustAnchors() {
        Set<TrustAnchor> trustAnchors = new HashSet<TrustAnchor>();
        for (Environment e : trustedEnvironments) {
            trustAnchors.add(new TrustAnchor(RootCertificates.lookupCertificate(e), null));
        }
        return trustAnchors;
    }

    /**
     * Gets <code>Collection</code> of trusted environments. An empty set is
     * returned if no environments are trusted or if the set of trusted
     * environments has not been set yet.
     */
    public static Collection<Environment> getTrustedEnvironments() {
        return new TreeSet<Environment>(trustedEnvironments);
	}

    public static Environment[] addInternalEnvironment(Environment environment, Environment[] environments) {
        // retrieve INTERNAL ENV from property file
        log.debug("Getting internal CA name from environment '" + environment.name() + "' from ooapi.properties");
        String internalCaName = OOAPIConfiguration.getInstance().getProperty(environment.name());
        log.debug("   Internal CA name = " + internalCaName  + " from ooapi.properties");

        if(internalCaName != null) {
            Environment internal  = Environment.valueOf(internalCaName);
            log.debug("   Environment = " + internal  + "");

            if(internal != null) {
                // make new array with INTERNAL ENV and return that
                Environment[] environmentsWithInternal = Arrays.copyOf(environments, environments.length + 1);
                environmentsWithInternal[environments.length] = internal;
                return environmentsWithInternal;
            }
        }
        return environments;
    }
}
