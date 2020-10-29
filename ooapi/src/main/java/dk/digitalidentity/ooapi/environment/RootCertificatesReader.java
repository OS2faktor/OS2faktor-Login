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

import dk.digitalidentity.ooapi.utils.FileReader;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is able to read root certificates from conf directory or from within the ooapi.jar
 * 
 * If rootcertificate file exists in conf folder then that root certificate is selected.
 * 
 * If not then we try looking inside ooapi resources.
 */
@Slf4j
public class RootCertificatesReader {
    private static final String CONFDIR_PROPERTY_KEY = "dk.certifikat.oces2.confdir";
    private static final String ROOTCERTIFICATEDIR_PROPERTY_KEY = "dk.certifikat.oces2.rootcertificatedir";

    public static String getRootCertificate(String filename) {
        String rootCertificate = null;

        String confDir = System.getProperty(ROOTCERTIFICATEDIR_PROPERTY_KEY);
        if (confDir == null) {
            confDir = System.getProperty(CONFDIR_PROPERTY_KEY);
        }
        if (confDir != null) {
            rootCertificate = FileReader.ReadFile(confDir, filename);
        }
        if (rootCertificate != null) {log.debug("Root certificate fetched from file: " + confDir + "/" + filename);}
        // if confDir does not contain file - try looking in ooapi(using FileReader).
        return rootCertificate == null ? FileReader.ReadFile("/" + filename) : rootCertificate;
    }
}