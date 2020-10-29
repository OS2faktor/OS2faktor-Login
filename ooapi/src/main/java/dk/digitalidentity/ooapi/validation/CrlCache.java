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

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import dk.digitalidentity.ooapi.exceptions.CrlNotYetValidException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrlCache {    
    private final Map<String, CrlCacheElement> crls = Collections.synchronizedMap(new HashMap<String, CrlCacheElement>());
    private final Map<String, CacheItemLock> locks = Collections.synchronizedMap(new HashMap<String, CacheItemLock>());
    private final int timeout;

    /**
     * @param timeout
     *            - The timeout in minutes of cached elements.
     */
    public CrlCache(int timeout) {
        this.timeout = timeout;
    }

    public CRL getCrl(String key) {
        return crls.get(key).getValue();
    }

    /**
     * @param key
     *            - the location of the crl / used as the map key
     * @param crl
     *            - the crl that the cache needs to be updated with
     */
    private void updateCrlCache(String key, CRL crl) {
        crls.put(key, new CrlCacheElement(crl));
    }

    /**
     * @param key
     *            - the location of the crl
     * @param job
     *            - the job that retrieves the crl
     * 
     *            Only one request must download crl therefore this method is synchronized and weuse a lock
     */
     public void downloadCrlAndUpdateCache(String key, DownloadableCrlJob job) {
         CacheItemLock lock = getLock(key);

         synchronized (lock){
             try {
                 lock.lock();

                 // we need to check that different thread didn't update the cache while current thread was waiting
                 if (isValid(key)) {
                     log.debug("Another request updated the cache, no need to execute new download");

                     lock.unlock();
                     return;
                 }

                 updateCrlCache(key, job.download());
                 log.debug("Downloaded a new CRL and updated the CRL cache!!");
             } finally {
                 lock.unlock();
             }

         }
    }

    public boolean isValid(String key) {
        CrlCacheElement cacheElement = crls.get(key);

        if (cacheElement == null) {
            log.debug("Cache missing for " + key);
            return false;
        }
        return isValid(cacheElement);
    }

    /**
     * Checks that the CRL is in cache and not expired and within timeout limit
     */
    protected boolean isValid(CrlCacheElement cacheElement) {
        return isCacheElementCrlValid(cacheElement) && isCacheElementOlderThanTimeout(cacheElement);
    }

    /**
     * @param key
     *            - key of crl
     * 
     *            Checks that the CLR list is in crls map with the given key is exists and not expired
     * 
     *            (we are not checking CRL timout or CRL download deadline)
     */
    public boolean checkOnlyIfCrlIsValid(String key) {
        CrlCacheElement cacheElement = crls.get(key);

        if (cacheElement == null) {
            log.debug("Cache missing for " + key);
            return false;
        }
        return isCacheElementCrlValid(cacheElement);
    }

    /**
     * @param cacheElement
     *            - the cacheElement containing the crl
     * 
     *            Checks that the CLR list is in the given cacheElement and not expired (we are not checking deadline)
     */
    private boolean isCacheElementCrlValid(CrlCacheElement cacheElement) {
        try {
            final boolean crlIsValid = cacheElement.getValue().isValid();
            if (!crlIsValid) {
                log.debug("Cache contains " + cacheElement + " but the crl is expired");
            }
            return crlIsValid;
        } catch (CrlNotYetValidException e) {
            log.debug("Cache contains " + cacheElement + " but the crl is not yet valid");
            return false;
        }
    }

    /**
     * This method checks if the deadline for the cache element is passed.
     * 
     * If cache element is older than timeout than timeout it is usable if not CRL should be refreshed.
     */
    private boolean isCacheElementOlderThanTimeout(CrlCacheElement cacheElement) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(cacheElement.creationDate);
        calendar.add(GregorianCalendar.MINUTE, timeout);
        final boolean cacheElementOlderThanTimeout = calendar.getTime().after(new Date());
        if (!cacheElementOlderThanTimeout) {
            log.debug("Cache contains element " + cacheElement + " but it is expired after cache timeout " + timeout + " minutes");
        }
        return cacheElementOlderThanTimeout;
    }

    static class CrlCacheElement {

        private CRL crl;
        private Date creationDate;

        public CrlCacheElement(CRL crl) {
            this(crl, new Date());
        }

        protected CrlCacheElement(CRL crl, Date creationDate) {
            this.crl = crl;
            this.creationDate = creationDate;
        }

        public Date getCreationDate() {
            return creationDate;
        }

        public CRL getValue() {
            return crl;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("creationDate", creationDate).append("crl", crl).toString();
        }
    }

    private class CacheItemLock {
        @SuppressWarnings("unused")
		String lockName;

        boolean isLocked;
        
        CacheItemLock(String lockName){
            this.lockName = lockName;
        }

        void lock(){
            isLocked = true;
        }

        void unlock(){
            isLocked = false;
        }
        
        boolean isLocked(){
            return isLocked;
        }
    }

    private CacheItemLock getLock(String key){
        synchronized (locks){
            if (!locks.containsKey(key)){
                locks.put(key, new CacheItemLock(key));
            }
            return locks.get(key);
        }
    }

    public boolean isLocked(String key) {
        return locks.containsKey(key) && locks.get(key).isLocked();
    }
}
