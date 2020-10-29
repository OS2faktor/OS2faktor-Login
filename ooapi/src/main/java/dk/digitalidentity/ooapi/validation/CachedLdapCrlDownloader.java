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

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import dk.digitalidentity.ooapi.config.OOAPIConfiguration;
import dk.digitalidentity.ooapi.environment.Environments.Environment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CachedLdapCrlDownloader {
    private LdapCrlDownloader downloader = new LdapCrlDownloader();
    private CrlCache crlCache;

    public CachedLdapCrlDownloader() {
        this.crlCache = new CrlCache(OOAPIConfiguration.getInstance().getLdapCrlCacheTimeout());
    }

    public CRL download(Environment environment, String ldapPath) {
        if (crlCache.isValid(ldapPath)) {
            log.debug("Cache contains valid element - using it - key: " + ldapPath);
            return crlCache.getCrl(ldapPath);
        } else if (crlCache.isLocked(ldapPath)) {
            // cache is NOT valid and it IS locked - lets see if cache contains CRL that is not expired
            if (crlCache.checkOnlyIfCrlIsValid(ldapPath)) {
                log.debug("Cache contains CRL that is not expired - we are using it");
                return crlCache.getCrl(ldapPath);
            } else {
                // current cache is locked and old crl is not valid - wait in queue for new CRL update
                log.debug("There is no CRL in cache - we have to wait in queue for download");
                return downloadNewCrl(environment, ldapPath);
            }
        } else {
            // CRL in cache is not valid and it IS NOT locked - retrieve new CRL
            log.debug("Try retrieving new CRL from source " + ldapPath);
            return downloadNewCrl(environment, ldapPath);
        }
    }

    private CRL downloadNewCrl(Environment environment, String ldapPath) {
        crlCache.downloadCrlAndUpdateCache(ldapPath, new LdapDownloadableJob(environment, ldapPath));
        return crlCache.getCrl(ldapPath);
    }

    private class LdapDownloadableJob implements DownloadableCrlJob {
        private String ldapPath;
        private Environment environment;

        public LdapDownloadableJob(Environment environment, String ldapPath) {
            this.environment = environment;
            this.ldapPath = ldapPath;
        }

        public CRL download() {
            return downloader.download(environment, ldapPath);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("ldapPath", ldapPath).toString();
        }
    }
}
