/*
 * Licensed to the University Corporation for Advanced Internet Development,
 * Inc. (UCAID) under one or more contributor license agreements.  See the
 * NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The UCAID licenses this file to You under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO: this was copied from OpenSAML 3.4.6, and all changes are outlined with CHANGE:

package org.opensaml.saml.metadata.resolver.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.metadata.resolver.ExtendedRefreshableMetadataResolver;
import org.opensaml.saml.metadata.resolver.filter.FilterException;
import org.opensaml.saml.saml2.common.SAML2Support;
import org.opensaml.saml.saml2.common.TimeBoundSAMLObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import net.shibboleth.utilities.java.support.annotation.Duration;
import net.shibboleth.utilities.java.support.annotation.constraint.Positive;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.primitive.TimerSupport;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

/**
 * Base class for metadata providers that cache and periodically refresh their metadata.
 * 
 * This metadata provider periodically checks to see if the read metadata file has changed. The delay between each
 * refresh interval is calculated as follows. If no validUntil or cacheDuration is present then the
 * {@link #getMaxRefreshDelay()} value is used. Otherwise, the earliest refresh interval of the metadata file is checked
 * by looking for the earliest of all the validUntil attributes and cacheDuration attributes. If that refresh interval
 * is larger than the max refresh delay then {@link #getMaxRefreshDelay()} is used. If that number is smaller than the
 * min refresh delay then {@link #getMinRefreshDelay()} is used. Otherwise the calculated refresh delay multiplied by
 * {@link #getRefreshDelayFactor()} is used. By using this factor, the provider will attempt to be refresh before the
 * cache actually expires, allowing a some room for error and recovery. Assuming the factor is not exceedingly close to
 * 1.0 and a min refresh delay that is not overly large, this refresh will likely occur a few times before the cache
 * expires.
 */
public abstract class AbstractReloadingMetadataResolver extends AbstractBatchMetadataResolver 
        implements ExtendedRefreshableMetadataResolver {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(AbstractReloadingMetadataResolver.class);

    /** Timer used to schedule background metadata update tasks. */
    private Timer taskTimer;
    
    /** Whether we created our own task timer during object construction. */
    private boolean createdOwnTaskTimer;
        
    /** Current task to refresh metadata. */
    private RefreshMetadataTask refreshMetadataTask;
    
    /** Factor used to compute when the next refresh interval will occur. Default value: 0.75 */
    private float refreshDelayFactor = 0.75f;

    /**
     * Refresh interval used when metadata does not contain any validUntil or cacheDuration information. Default value:
     * 14400000ms (4 hours).
     */
    @Duration @Positive private long maxRefreshDelay = 14400000;

    /** Floor, in milliseconds, for the refresh interval. Default value: 300000ms (5 minutes). */
    @Duration @Positive private long minRefreshDelay = 300000;

    /** Time when the currently cached metadata file expires. */
    private DateTime expirationTime;
    
    /** Impending expiration warning threshold for metadata refresh, in milliseconds. 
     * Default value: 0ms (disabled). */
    @Duration @Positive private long expirationWarningThreshold;

    /** Last time the metadata was updated. */
    private DateTime lastUpdate;

    /** Last time a refresh cycle occurred. */
    private DateTime lastRefresh;

    /** Next time a refresh cycle will occur. */
    private DateTime nextRefresh;
    
    /** Last time a successful refresh cycle occurred. */
    private DateTime lastSuccessfulRefresh;

    /** Flag indicating whether last refresh cycle was successful. */
    private Boolean wasLastRefreshSuccess;
    
    /** Internal flag for tracking success during the refresh operation. */
    private boolean trackRefreshSuccess;
    


    /** Constructor. */
    protected AbstractReloadingMetadataResolver() {
        this(null);
    }

    /**
     * Constructor.
     * 
     * @param backgroundTaskTimer time used to schedule background refresh tasks
     */
    protected AbstractReloadingMetadataResolver(@Nullable final Timer backgroundTaskTimer) {
        super();
        
        setCacheSourceMetadata(true);
        
        if (backgroundTaskTimer == null) {
            taskTimer = new Timer(TimerSupport.getTimerName(this), true);
            createdOwnTaskTimer = true;
        } else {
            taskTimer = backgroundTaskTimer;
        }
    }
    
    /** {@inheritDoc} */
    @Override
    protected void setCacheSourceMetadata(final boolean flag) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);

        if (!flag) {
            log.warn("{} Caching of source metadata may not be disabled for reloading metadata resolvers", 
                    getLogPrefix());
        } else {
            super.setCacheSourceMetadata(flag);
        }
    }

    /**
     * Gets the time when the currently cached metadata expires.
     * 
     * @return time when the currently cached metadata expires, or null if no metadata is cached
     */
    public DateTime getExpirationTime() {
        return expirationTime;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable public DateTime getLastUpdate() {
        return lastUpdate;
    }

    /** {@inheritDoc} */
    @Override @Nullable public DateTime getLastRefresh() {
        return lastRefresh;
    }
    
    /** {@inheritDoc} */
    @Nullable public DateTime getLastSuccessfulRefresh() {
        return lastSuccessfulRefresh;
    }

    /** {@inheritDoc} */
    @Nullable public Boolean wasLastRefreshSuccess() {
        return wasLastRefreshSuccess;
    }

    /**
     * Gets the time when the next refresh cycle will occur.
     * 
     * @return time when the next refresh cycle will occur
     */
    public DateTime getNextRefresh() {
        return nextRefresh;
    }

    /**
     * Gets the impending expiration warning threshold used at refresh time.
     * 
     * @return threshold for logging a warning if live metadata will soon expire
     */
    @Duration public long getExpirationWarningThreshold() {
        return expirationWarningThreshold;
    }

    /**
     * Sets the impending expiration warning threshold used at refresh time.
     * 
     * @param threshold the threshold for logging a warning if live metadata will soon expire
     */
    @Duration public void setExpirationWarningThreshold(@Duration @Positive final long threshold) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        
        if (threshold < 0) {
            throw new IllegalArgumentException("Expiration warning threshold must be greater than or equal to 0");
        }
        expirationWarningThreshold = threshold;
    }
    /**
     * Gets the maximum amount of time, in milliseconds, between refresh intervals.
     * 
     * @return maximum amount of time between refresh intervals
     */
    @Duration public long getMaxRefreshDelay() {
        return maxRefreshDelay;
    }

    /**
     * Sets the maximum amount of time, in milliseconds, between refresh intervals.
     * 
     * @param delay maximum amount of time, in milliseconds, between refresh intervals
     */
    @Duration public void setMaxRefreshDelay(@Duration @Positive final long delay) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        
        if (delay < 0) {
            throw new IllegalArgumentException("Maximum refresh delay must be greater than 0");
        }
        maxRefreshDelay = delay;
    }

    /**
     * Gets the delay factor used to compute the next refresh time.
     * 
     * @return delay factor used to compute the next refresh time
     */
    public float getRefreshDelayFactor() {
        return refreshDelayFactor;
    }

    /**
     * Sets the delay factor used to compute the next refresh time. The delay must be between 0.0 and 1.0, exclusive.
     * 
     * @param factor delay factor used to compute the next refresh time
     */
    public void setRefreshDelayFactor(final float factor) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);

        if (factor <= 0 || factor >= 1) {
            throw new IllegalArgumentException("Refresh delay factor must be a number between 0.0 and 1.0, exclusive");
        }

        refreshDelayFactor = factor;
    }

    /**
     * Gets the minimum amount of time, in milliseconds, between refreshes.
     * 
     * @return minimum amount of time, in milliseconds, between refreshes
     */
    @Duration public long getMinRefreshDelay() {
        return minRefreshDelay;
    }

    /**
     * Sets the minimum amount of time, in milliseconds, between refreshes.
     * 
     * @param delay minimum amount of time, in milliseconds, between refreshes
     */
    @Duration public void setMinRefreshDelay(@Duration @Positive final long delay) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);

        if (delay < 0) {
            throw new IllegalArgumentException("Minimum refresh delay must be greater than 0");
        }
        minRefreshDelay = delay;
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        refreshMetadataTask.cancel();
        
        if (createdOwnTaskTimer) {
            taskTimer.cancel();
        }
        
        expirationTime = null;
        lastRefresh = null;
        lastUpdate = null;
        nextRefresh = null;
        
        super.doDestroy();
    }

    /** {@inheritDoc} */
    @Override
    protected void initMetadataResolver() throws ComponentInitializationException {
        super.initMetadataResolver();
        
        try {
            refresh();
        } catch (final ResolverException e) {
            throw new ComponentInitializationException("Error refreshing metadata during init", e);
        }
        
        if (minRefreshDelay > maxRefreshDelay) {
            throw new ComponentInitializationException("Minimum refresh delay " + minRefreshDelay
                    + " is greater than maximum refresh delay " + maxRefreshDelay);
        }
    }

    /**
     * Refreshes the metadata from its source.
     * 
     * @throws ResolverException thrown is there is a problem retrieving and processing the metadata
     */
    @Override
    public synchronized void refresh() throws ResolverException {
        DateTime now = null;
        String mdId = null;
        trackRefreshSuccess = false;

        try {
            // In case a destroy() thread beat this thread into the monitor.
            if (isDestroyed()) {
                return;
            }

            // A manual refresh() must cancel the previously-scheduled future task, since will (re)schedule its own.
            // If this execution *is* the task, it's ok to cancel ourself, we're already running.
            if (refreshMetadataTask != null) {
                refreshMetadataTask.cancel();
            }
            
            now = new DateTime(ISOChronology.getInstanceUTC());
            mdId = getMetadataIdentifier();

            log.debug("{} Beginning refresh of metadata from '{}'", getLogPrefix(), mdId);
        
            final byte[] mdBytes = fetchMetadata();
            if (mdBytes == null) {
                log.info("{} Metadata from '{}' has not changed since last refresh", getLogPrefix(), mdId);
                processCachedMetadata(mdId, now);
            } else {
                log.debug("{} Processing new metadata from '{}'", getLogPrefix(), mdId);
                processNewMetadata(mdId, now, mdBytes);
            }
        } catch (final Throwable t) {
            trackRefreshSuccess = false;
            nextRefresh = new DateTime(ISOChronology.getInstanceUTC()).plus(computeNextRefreshDelay(null));
            if (t instanceof Exception) {
            	if (timeoutException((Exception) t)) {
	            	// CHANGE: added t as parameter to logging
	                //log.error("{} Error occurred while attempting to refresh metadata from '{}'", getLogPrefix(), mdId);
	            	log.warn(getLogPrefix() + " Failure occurred while attempting to refresh metadata from '" + mdId + "'", t);
	                throw new ResolverException((Exception) t);
            	}
            	else {
	            	// CHANGE: added t as parameter to logging
	                //log.error("{} Error occurred while attempting to refresh metadata from '{}'", getLogPrefix(), mdId);
	            	log.warn(getLogPrefix() + " Failure occurred while attempting to refresh metadata from '" + mdId + "'", t);
	                throw new ResolverException((Exception) t);            		
            	}
            } else {
            	// CHANGE: added t as parameter to logging
                //log.error("{} Error occurred while attempting to refresh metadata from '{}'", getLogPrefix(), mdId, t);
                log.warn(getLogPrefix() + " Failure occurred while attempting to refresh metadata from '" + mdId + "'", t);
                throw new ResolverException(String.format("Saw an error of type '%s' with message '%s'", 
                        t.getClass().getName(), t.getMessage()));
            }
        } finally {
            logCachedMetadataExpiration(now);
            
            if (trackRefreshSuccess) {
                wasLastRefreshSuccess = true;
                lastSuccessfulRefresh = now;
            } else {
                wasLastRefreshSuccess = false;
            }
            
            refreshMetadataTask = new RefreshMetadataTask();
            final long nextRefreshDelay = nextRefresh.getMillis() - System.currentTimeMillis();
            taskTimer.schedule(refreshMetadataTask, nextRefreshDelay);
            log.info("{} Next refresh cycle for metadata provider '{}' will occur on '{}' ('{}' local time)",
                    new Object[] {getLogPrefix(), mdId, nextRefresh, 
                            nextRefresh.toDateTime(DateTimeZone.getDefault()),});
            lastRefresh = now;
        }
    }

    // CHANGE: Added this helper method
    private boolean timeoutException(Exception t) {
    	if (t instanceof ConnectException && t.getMessage() != null && t.getMessage().contains("Connection timed out")) {
    		return true;
    	}
    	
    	if (t.getCause() != null && t.getCause() instanceof Exception) {
    		return timeoutException((Exception) t.getCause());
    	}
    	
    	return false;
	}

	/**
     * Check cached metadata for expiration or pending expiration and log appropriately.
     *
     * @param now the current date/time
     */
    private void logCachedMetadataExpiration(@Nonnull final DateTime now) {
        final String mdId = getMetadataIdentifier();
        final XMLObject cached = getBackingStore().getCachedOriginalMetadata();
        if (cached != null && !isValid(cached)) {
            log.warn("{} Metadata root from '{}' currently live (post-refresh) is expired or otherwise invalid",
                    getLogPrefix(), mdId);
        } else if (cached instanceof TimeBoundSAMLObject) {
            final TimeBoundSAMLObject timebound = (TimeBoundSAMLObject) cached;
            if (isRequireValidMetadata() && timebound.getValidUntil() != null) {
                if (getExpirationWarningThreshold() > 0 
                        && timebound.getValidUntil().isBefore(now.plus(getExpirationWarningThreshold()))) {
                    log.warn("{} Metadata root from '{}' currently live (post-refresh) will expire "
                            + "within the configured threshhold at '{}'",
                            getLogPrefix(), mdId, timebound.getValidUntil());
                } else if (timebound.getValidUntil().isBefore(nextRefresh)) {
                    log.warn("{} Metadata root from '{}' currently live (post-refresh) will expire "
                            + "at '{}' before the next refresh scheduled for {}'",
                            getLogPrefix(), mdId, timebound.getValidUntil(), nextRefresh);
                }
            }
        }
    }

    /**
     * Gets an identifier which may be used to distinguish this metadata in logging statements.
     * 
     * @return identifier which may be used to distinguish this metadata in logging statements
     */
    protected abstract String getMetadataIdentifier();

    /**
     * Fetches metadata from a source.
     * 
     * @return the fetched metadata, or null if the metadata is known not to have changed since the last retrieval
     * 
     * @throws ResolverException thrown if there is a problem fetching the metadata
     */
    protected abstract byte[] fetchMetadata() throws ResolverException;

    /**
     * Unmarshalls the given metadata bytes.
     * 
     * @param metadataBytes raw metadata bytes
     * 
     * @return the metadata
     * 
     * @throws ResolverException thrown if the metadata can not be unmarshalled
     */
    protected XMLObject unmarshallMetadata(final byte[] metadataBytes) throws ResolverException {
        try {
            return unmarshallMetadata(new ByteArrayInputStream(metadataBytes));
        } catch (final UnmarshallingException e) {
            final String errorMsg = "Unable to unmarshall metadata";
            log.error("{} " + errorMsg, getLogPrefix());
            throw new ResolverException(errorMsg, e);
        }
    }

    /**
     * Processes a cached metadata document in order to determine, and schedule, the next time it should be refreshed.
     * 
     * @param metadataIdentifier identifier of the metadata source
     * @param refreshStart when the current refresh cycle started
     * 
     * @throws ResolverException throw is there is a problem process the cached metadata
     */
    protected void processCachedMetadata(final String metadataIdentifier, final DateTime refreshStart)
            throws ResolverException {
        log.debug("{} Computing new expiration time for cached metadata from '{}'", getLogPrefix(), metadataIdentifier);
        final DateTime metadataExpirationTime = 
                SAML2Support.getEarliestExpiration(getBackingStore().getCachedOriginalMetadata(),
                refreshStart.plus(getMaxRefreshDelay()), refreshStart);

        trackRefreshSuccess = true;
        expirationTime = metadataExpirationTime;
        final long nextRefreshDelay = computeNextRefreshDelay(expirationTime);
        nextRefresh = new DateTime(ISOChronology.getInstanceUTC()).plus(nextRefreshDelay);
    }

    /**
     * Process a new metadata document. Processing include unmarshalling and filtering metadata, determining the next
     * time is should be refreshed and scheduling the next refresh cycle.
     * 
     * @param metadataIdentifier identifier of the metadata source
     * @param refreshStart when the current refresh cycle started
     * @param metadataBytes raw bytes of the new metadata document
     * 
     * @throws ResolverException thrown if there is a problem unmarshalling or filtering the new metadata
     */
    protected void processNewMetadata(final String metadataIdentifier, final DateTime refreshStart,
            final byte[] metadataBytes) throws ResolverException {
        log.debug("{} Unmarshalling metadata from '{}'", getLogPrefix(), metadataIdentifier);
        final XMLObject metadata = unmarshallMetadata(metadataBytes);

        if (!isValid(metadata)) {
            processPreExpiredMetadata(metadataIdentifier, refreshStart, metadataBytes, metadata);
        } else {
            processNonExpiredMetadata(metadataIdentifier, refreshStart, metadataBytes, metadata);
        }
    }

    /**
     * Processes metadata that has been determined to be invalid (usually because it's already expired) at the time it
     * was fetched. A metadata document is considered be invalid if its root element returns false when passed to the
     * {@link #isValid(XMLObject)} method.
     * 
     * @param metadataIdentifier identifier of the metadata source
     * @param refreshStart when the current refresh cycle started
     * @param metadataBytes raw bytes of the new metadata document
     * @param metadata new metadata document unmarshalled
     */
    protected void processPreExpiredMetadata(final String metadataIdentifier, final DateTime refreshStart,
            final byte[] metadataBytes, final XMLObject metadata) {
        log.warn("{} Entire metadata document from '{}' was expired at time of loading, existing metadata retained",
                getLogPrefix(), metadataIdentifier);

        nextRefresh = new DateTime(ISOChronology.getInstanceUTC()).plus(computeNextRefreshDelay(null));
        trackRefreshSuccess = false;
    }

    /**
     * Processes metadata that has been determined to be valid at the time it was fetched. A metadata document is
     * considered to be valid if its root element returns true when passed to the {@link #isValid(XMLObject)} method.
     * 
     * @param metadataIdentifier identifier of the metadata source
     * @param refreshStart when the current refresh cycle started
     * @param metadataBytes raw bytes of the new metadata document
     * @param metadata new metadata document unmarshalled
     * 
     * @throws ResolverException thrown if there s a problem processing the metadata
     */
    protected void processNonExpiredMetadata(final String metadataIdentifier, final DateTime refreshStart,
            final byte[] metadataBytes, final XMLObject metadata) throws ResolverException {
        final Document metadataDom = metadata.getDOM().getOwnerDocument();

        log.debug("{} Preprocessing metadata from '{}'", getLogPrefix(), metadataIdentifier);
        BatchEntityBackingStore newBackingStore = null;
        try {
            newBackingStore = preProcessNewMetadata(metadata);
        } catch (final FilterException e) {
            final String errMsg = "Error filtering metadata from " + metadataIdentifier;
            log.error("{} " + errMsg, getLogPrefix(), e);
            throw new ResolverException(errMsg, e);
        }

        log.debug("{} Releasing cached DOM for metadata from '{}'", getLogPrefix(), metadataIdentifier);
        releaseMetadataDOM(newBackingStore.getCachedOriginalMetadata());
        releaseMetadataDOM(newBackingStore.getCachedFilteredMetadata());

        log.debug("{} Post-processing metadata from '{}'", getLogPrefix(), metadataIdentifier);
        postProcessMetadata(metadataBytes, metadataDom, newBackingStore.getCachedOriginalMetadata(), 
                newBackingStore.getCachedFilteredMetadata());

        log.debug("{} Computing expiration time for metadata from '{}'", getLogPrefix(), metadataIdentifier);
        // Note: As noted in its Javadocs, technically this method can sometimes return null, but won't in this case
        // since the candidate time (2nd arg) is not null.
        final DateTime metadataExpirationTime = SAML2Support.getEarliestExpiration(
                newBackingStore.getCachedOriginalMetadata(), refreshStart.plus(getMaxRefreshDelay()), refreshStart);
        log.debug("{} Expiration of metadata from '{}' will occur at {}", getLogPrefix(), metadataIdentifier, 
                metadataExpirationTime.toString());

        // This is where the new processed data becomes effective. Exceptions thrown prior to this point
        // therefore result in the old data being kept effective.
        setBackingStore(newBackingStore);
        
        lastUpdate = refreshStart;
        trackRefreshSuccess = true;
        
        final long nextRefreshDelay;
        if (metadataExpirationTime.isBeforeNow()) {
            expirationTime = new DateTime(ISOChronology.getInstanceUTC()).plus(getMinRefreshDelay());
            nextRefreshDelay = getMaxRefreshDelay();
        } else {
            expirationTime = metadataExpirationTime;
            nextRefreshDelay = computeNextRefreshDelay(expirationTime);
        }
        nextRefresh = new DateTime(ISOChronology.getInstanceUTC()).plus(nextRefreshDelay);

        log.info("{} New metadata successfully loaded for '{}'", getLogPrefix(), getMetadataIdentifier());
    }

    /**
     * Post-processing hook called after new metadata has been unmarshalled, filtered, and the DOM released (from the
     * {@link XMLObject}) but before the metadata is saved off. Any exception thrown by this hook will cause the
     * retrieved metadata to be discarded.
     * 
     * The default implementation of this method is a no-op
     * 
     * @param metadataBytes original raw metadata bytes retrieved via {@link #fetchMetadata}
     * @param metadataDom original metadata after it has been parsed in to a DOM document
     * @param originalMetadata original metadata prior to being filtered, with its DOM released
     * @param filteredMetadata metadata after it has been run through all registered filters and its DOM released
     * 
     * @throws ResolverException thrown if there is a problem with the provided data
     */
    protected void postProcessMetadata(final byte[] metadataBytes, final Document metadataDom,
            final XMLObject originalMetadata, final XMLObject filteredMetadata) throws ResolverException {

    }

    /**
     * Computes the delay until the next refresh time based on the current metadata's expiration time and the refresh
     * interval floor.
     * 
     * @param expectedExpiration the time when the metadata is expected to expire and need refreshing
     * 
     * @return delay, in milliseconds, until the next refresh time
     */
    protected long computeNextRefreshDelay(final DateTime expectedExpiration) {
        final long now = new DateTime(ISOChronology.getInstanceUTC()).getMillis();

        long expireInstant = 0;
        if (expectedExpiration != null) {
            expireInstant = expectedExpiration.toDateTime(ISOChronology.getInstanceUTC()).getMillis();
        }
        long refreshDelay = (long) ((expireInstant - now) * getRefreshDelayFactor());

        // if the expiration time was null or the calculated refresh delay was less than the floor
        // use the floor
        if (refreshDelay < getMinRefreshDelay()) {
            refreshDelay = getMinRefreshDelay();
        }

        return refreshDelay;
    }

    /**
     * Converts an InputStream into a byte array.
     * 
     * @param ins input stream to convert
     * 
     * @return resultant byte array
     * 
     * @throws ResolverException thrown if there is a problem reading the resultant byte array
     */
    protected byte[] inputstreamToByteArray(final InputStream ins) throws ResolverException {
        try {
            // 1 MB read buffer
            final byte[] buffer = new byte[1024 * 1024];
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            int n = 0;
            while (-1 != (n = ins.read(buffer))) {
                output.write(buffer, 0, n);
            }

            ins.close();
            return output.toByteArray();
        } catch (final IOException e) {
            throw new ResolverException(e);
        }
    }

    /** Background task that refreshes metadata. */
    private class RefreshMetadataTask extends TimerTask {

        /** {@inheritDoc} */
        //CheckStyle: ReturnCount OFF
        @Override public void run() {
            try {
                if (isDestroyed()) {
                    // just in case the metadata provider was destroyed before this task runs
                    return;
                }
                
                refresh();
            } catch (final ResolverException e) {
                // nothing to do, error message already logged by refreshMetadata()
                return;
            }
        }
        //CheckStyle: ReturnCount ON
    }
}
