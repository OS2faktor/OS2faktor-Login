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
package dk.digitalidentity.ooapi.ldap;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import dk.digitalidentity.ooapi.config.OOAPIConfiguration;
import dk.digitalidentity.ooapi.environment.Environments;
import dk.digitalidentity.ooapi.environment.Environments.Environment;
import dk.digitalidentity.ooapi.serviceprovider.ServiceProviderException;

public class LDAPFactory {

    public static DirContext createLdapContext(Environment environment, String... binaryLdapAttributes) {
        OOAPIConfiguration configuration = OOAPIConfiguration.getInstance();
        String ldapServerName = configuration.getProperty("ldap.server.danid." + environment);
        return createLdapContext(ldapServerName, binaryLdapAttributes);
    }

    public static InitialDirContext createLdapContext(String ldapServerName, String... binaryLdapAttributes) {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.PROVIDER_URL, "ldap://" + ldapServerName + "/");
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");

        int timeout = OOAPIConfiguration.getInstance().getHttpSocketAndLDAPTimeout() * 60 * 1000;// timeout in minutes
        env.put("com.sun.jndi.ldap.read.timeout", "" + timeout);

        for (int i = 0; i < binaryLdapAttributes.length; i++) {
            env.put("java.naming.ldap.attributes.binary", binaryLdapAttributes[i]);
        }
        try {
            return new InitialDirContext(env);
        } catch (NamingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String getLdapHostNamefromCaDN(String caDN) {
        OOAPIConfiguration configuration = OOAPIConfiguration.getInstance();
        String environment = configuration.getProperty("ldap.ca.cn." + caDN);
        if (environment == null) {
            for (Environment trustedEnvironment : Environments.getTrustedEnvironments()) {
                if (trustedEnvironment.name().startsWith("OCESII")) {
                    environment = trustedEnvironment.name();
                }
            }
        }
        return configuration.getProperty("ldap.server.danid." + environment);
    }

    public static String getLdapHostNameFromCaCommonName(String caCommonName) {
        OOAPIConfiguration configuration = OOAPIConfiguration.getInstance();
        String environment = configuration.getProperty("ldap.ca.cn." + caCommonName.toUpperCase().replace(" ", "_"));
        if (environment == null) {
            for (Environment trustedEnvironment : Environments.getTrustedEnvironments()) {
                if (trustedEnvironment.name().startsWith("OCESII")) {
                    environment = trustedEnvironment.name();
                }
            }
        }
        return configuration.getProperty("ldap.server.danid." + environment);
    }

    public static String getFullLdapPathFromCaCommonName(String caCommonName) {
        OOAPIConfiguration configuration = OOAPIConfiguration.getInstance();
        String environment = configuration.getProperty("ldap.ca.cn." + caCommonName.toUpperCase().replace(" ", "_"));
        if (environment == null) {
            throw new IllegalArgumentException("Unknown CA Common Name '" + caCommonName + "'");
        }

        String searchbase = configuration.getProperty("crl.searchbase." + environment);
        if (searchbase == null) {
            throw new IllegalStateException("Could not find searchbase property");
        }
        return "cn=" + caCommonName + "," + searchbase;
    }

    public static String getEnvironmentCaDn(Environment environment) throws ServiceProviderException {
        OOAPIConfiguration configuration = OOAPIConfiguration.getInstance();
        return configuration.getProperty("ldap.ca.dn.danid." + environment);
    }

    public static String getEnvironmentFromCaCommonName(String caDN) {
        //Ex. of caDN: C=DK, O=TRUST2408, CN=CA FOR SYSTEM CERTIFICATES UT   | we want the value CA FOR SYSTEM CERTIFICATES UT

        OOAPIConfiguration configuration = OOAPIConfiguration.getInstance();
        String commonName = null;
        if(caDN.contains("CN=")) {
            commonName = caDN.split("CN=")[1].toUpperCase().replace(" ", "_");
        }
        if(commonName != null) {
            commonName = commonName.contains(",") ? commonName.split(",")[0] : commonName;
        }
        
        return configuration.getProperty("ldap.ca.cn." + commonName);
    }
}
