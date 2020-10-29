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
package dk.digitalidentity.ooapi.config;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * This class is able to read property values from the ooapi.properties properties file
 */
@Slf4j
public class OOAPIConfiguration {
    private static final String OOAPI_PROPERTIES_FILENAME = "ooapi.properties";
    private static final String CONFDIR_PROPERTY_KEY = "dk.certifikat.oces2.confdir";

    public static OOAPIConfiguration instance;
    private Properties properties;

    private int httpSocketAndLDAPTimeout = 0;
    private int httpCrlCacheTimeout = 0;
    private int ldapCrlCacheTimeout = 0;

    private OOAPIConfiguration() {
        try {
            properties = new Properties();
            properties.load(this.getClass().getResourceAsStream("/" + OOAPI_PROPERTIES_FILENAME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String confDir = System.getProperty(CONFDIR_PROPERTY_KEY);
        if (confDir != null) {
            try {
                File ooapiFile = new File(confDir, OOAPI_PROPERTIES_FILENAME);
                if (ooapiFile.exists()) {
                    FileInputStream fileInputStream = new FileInputStream(ooapiFile);
                    try {
                        properties.load(fileInputStream);
                    } finally {
                        fileInputStream.close();
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        logConfigurationOriginText();
    }

    public static OOAPIConfiguration getInstance() {
        if (instance == null) {
            instance = new OOAPIConfiguration();
        }
        return instance;
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public void logConfigurationOriginText() {
        log.debug("Key origins:");
        for (String name: properties.stringPropertyNames()) {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" from ").append(OOAPI_PROPERTIES_FILENAME);
            if (name.toLowerCase().indexOf("password") == -1) {
                sb.append("  ::  ").append(this.properties.get(name));
            }
            log.debug(sb.toString());
        }
    }

    /**
     * Retrieves the HTTP Socket timeout for URL Connections in minutes - if property is not set timeout is set to default value 2.
     * 
     * This values is also used in LDAP connection timeout
     * 
     * If socket timeout is set to 0 thread will wait infinite, and this could possible lead to threads hanging and making the server crash. 
     * 
     * @return
     */
    public int getHttpSocketAndLDAPTimeout() {
        String timeoutProperty = getProperty("http.socket.and.ldap.timeout");
        httpSocketAndLDAPTimeout = timeoutProperty == null ? 2 : Integer.parseInt(timeoutProperty);
        
        return httpSocketAndLDAPTimeout;
    }
    /**
     * Retrieves the timeout for HTTP CRL Cache from property file - if property is not set timeout is set to default value 30
     * 
     * @return
     */
    public int getHttpCrlCacheTimeout() {
        String timeoutProperty = getProperty("crl.cache.timeout.http");
        httpCrlCacheTimeout = timeoutProperty == null ? 30 : Integer.parseInt(timeoutProperty);

        return httpCrlCacheTimeout;
    }

    /**
     * Retrieves the timeout for LDAP CRL Cache from property file - if property is not set timeout is set to default value 30
     * 
     * @return
     */
    public int getLdapCrlCacheTimeout() {
        String timeoutProperty = getProperty("crl.cache.timeout.ldap");
        ldapCrlCacheTimeout = timeoutProperty == null ? 30 : Integer.parseInt(timeoutProperty);

        return ldapCrlCacheTimeout;
    }

    /**
     * used to overwrite a single property Used during testing to setup scenarios.
     * 
     * Should ONLY be used for TESTING purpose
     * 
     */
    public void overwriteProperty(String key, String value) {
        if (properties != null) {
            properties.setProperty(key, value);
        }
    }
}
