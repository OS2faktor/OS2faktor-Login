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

package org.opensaml.saml.metadata.resolver.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.shibboleth.utilities.java.support.annotation.constraint.NonnullAfterInit;
import net.shibboleth.utilities.java.support.annotation.constraint.NonnullElements;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import net.shibboleth.utilities.java.support.component.AbstractIdentifiableInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;
import net.shibboleth.utilities.java.support.primitive.StringSupport;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.CriterionPredicateRegistry;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.resolver.ResolverSupport;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import net.shibboleth.utilities.java.support.xml.QNameSupport;

import org.opensaml.core.criterion.SatisfyAnyCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.metadata.criteria.entity.EvaluableEntityDescriptorCriterion;
import org.opensaml.saml.metadata.criteria.entity.impl.EntityDescriptorCriterionPredicateRegistry;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.filter.FilterException;
import org.opensaml.saml.metadata.resolver.filter.MetadataFilter;
import org.opensaml.saml.saml2.common.SAML2Support;
import org.opensaml.saml.saml2.metadata.EntitiesDescriptor;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

// copied from 3.4.6 - all changes commented with CHANGE (just log-error to log-warn)

/** An abstract, base, implementation of a metadata provider. */
public abstract class AbstractMetadataResolver extends AbstractIdentifiableInitializableComponent implements
        MetadataResolver {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(AbstractMetadataResolver.class);

    /** Unmarshaller factory used to get an unmarshaller for the metadata DOM. */
    private UnmarshallerFactory unmarshallerFactory;

    /** Whether metadata is required to be valid. */
    private boolean requireValidMetadata;

    /** Filter applied to all metadata. */
    private MetadataFilter mdFilter;
    
    /** Logging prefix. */
    private String logPrefix;
    
    /**
     * Whether problems during initialization should cause the provider to fail or go on without metadata. The
     * assumption being that in most cases a provider will recover at some point in the future. Default: true.
     */
    private boolean failFastInitialization;

    /** Backing store for runtime EntityDescriptor data. */
    private EntityBackingStore entityBackingStore;

    /** Pool of parsers used to process XML. */
    private ParserPool parser;
    
    /** Flag which determines whether predicates used in filtering are connected by 
     * a logical 'OR' (true) or by logical 'AND' (false). Defaults to false. */
    private boolean satisfyAnyPredicates;
    
    /** Registry used in resolving predicates from criteria. */
    private CriterionPredicateRegistry<EntityDescriptor> criterionPredicateRegistry;
    
    /** Flag which determines whether the default predicate registry will be used if one is not supplied explicitly.
     * Defaults to true. */
    private boolean useDefaultPredicateRegistry;
    
    /** Constructor. */
    public AbstractMetadataResolver() {
        failFastInitialization = true;
        requireValidMetadata = true;
        unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
        useDefaultPredicateRegistry = true;
    }

    /** {@inheritDoc} */
    @Override public boolean isRequireValidMetadata() {
        return requireValidMetadata;
    }

    /** {@inheritDoc} */
    @Override public void setRequireValidMetadata(final boolean require) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        requireValidMetadata = require;
    }

    /** {@inheritDoc} */
    @Override @Nullable public MetadataFilter getMetadataFilter() {
        return mdFilter;
    }

    /** {@inheritDoc} */
    @Override public void setMetadataFilter(@Nullable final MetadataFilter newFilter) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        mdFilter = newFilter;
    }

    /**
     * Gets whether problems during initialization should cause the provider to fail or go on without metadata. The
     * assumption being that in most cases a provider will recover at some point in the future.
     * 
     * @return whether problems during initialization should cause the provider to fail
     */
    public boolean isFailFastInitialization() {
        return failFastInitialization;
    }

    /**
     * Sets whether problems during initialization should cause the provider to fail or go on without metadata. The
     * assumption being that in most cases a provider will recover at some point in the future.
     * 
     * @param failFast whether problems during initialization should cause the provider to fail
     */
    public void setFailFastInitialization(final boolean failFast) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        failFastInitialization = failFast;
    }

    /**
     * Gets the pool of parsers to use to parse XML.
     * 
     * @return pool of parsers to use to parse XML
     */
    @Nonnull public ParserPool getParserPool() {
        return parser;
    }

    /**
     * Sets the pool of parsers to use to parse XML.
     * 
     * @param pool pool of parsers to use to parse XML
     */
    public void setParserPool(@Nonnull final ParserPool pool) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        parser = Constraint.isNotNull(pool, "ParserPool may not be null");
    }

    /**
     * Get the flag indicating whether resolved credentials may satisfy any predicates 
     * (i.e. connected by logical 'OR') or all predicates (connected by logical 'AND').
     * 
     * <p>Defaults to false.</p>
     * 
     * @return true if must satisfy all, false otherwise
     */
    public boolean isSatisfyAnyPredicates() {
        return satisfyAnyPredicates;
    }

    /**
     * Set the flag indicating whether resolved credentials may satisfy any predicates 
     * (i.e. connected by logical 'OR') or all predicates (connected by logical 'AND').
     * 
     * <p>Defaults to false.</p>
     * 
     * @param flag true if must satisfy all, false otherwise
     */
    public void setSatisfyAnyPredicates(final boolean flag) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        satisfyAnyPredicates = flag;
    }

    /**
     * Get the registry used in resolving predicates from criteria.
     * 
     * @return the effective registry instance used
     */
    @NonnullAfterInit public CriterionPredicateRegistry<EntityDescriptor> getCriterionPredicateRegistry() {
        return criterionPredicateRegistry;
    }

    /**
     * Set the registry used in resolving predicates from criteria.
     * 
     * @param registry the registry instance to use
     */
    public void setCriterionPredicateRegistry(@Nullable final CriterionPredicateRegistry<EntityDescriptor> registry) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        criterionPredicateRegistry = registry;
    }

    /**
     * Get the flag which determines whether the default predicate registry will be used 
     * if one is not supplied explicitly.
     * 
     * <p>Defaults to true.</p>
     * 
     * @return true if should use default registry, false otherwise
     */
    public boolean isUseDefaultPredicateRegistry() {
        return useDefaultPredicateRegistry;
    }

    /**
     * Set the flag which determines whether the default predicate registry will be used 
     * if one is not supplied explicitly.
     * 
     * <p>Defaults to true.</p>
     * 
     * @param flag true if should use default registry, false otherwise
     */
    public void setUseDefaultPredicateRegistry(final boolean flag) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        useDefaultPredicateRegistry = flag;
    }

    /** {@inheritDoc} */
    @Override @Nullable public EntityDescriptor resolveSingle(final CriteriaSet criteria) throws ResolverException {
        ComponentSupport.ifNotInitializedThrowUninitializedComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);

        final Iterable<EntityDescriptor> iterable = resolve(criteria);
        if (iterable != null) {
            final Iterator<EntityDescriptor> iterator = iterable.iterator();
            if (iterator != null && iterator.hasNext()) {
                return iterator.next();
            }
        }
        return null;
    }
    
    /**
     * Get the XMLObject unmarshaller factory to use.
     * 
     * @return the unmarshaller factory instance to use
     */
    protected UnmarshallerFactory getUnmarshallerFactory() {
        return unmarshallerFactory;
    }

    /** {@inheritDoc} */
    @Override protected final void doInitialize() throws ComponentInitializationException {
        super.doInitialize();

        try {
            initMetadataResolver();
        } catch (final ComponentInitializationException e) {
            if (failFastInitialization) {
            	// CHANGE: modified error to warn
                log.warn("{} Metadata provider failed to properly initialize, fail-fast=true, halting", 
                        getLogPrefix());
                throw e;
            } else {
            	// CHANGE: modified error to warn
                log.warn("{} Metadata provider failed to properly initialize, fail-fast=false, "
                        + "continuing on in a degraded state", getLogPrefix(), e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected void doDestroy() {
        unmarshallerFactory = null;
        mdFilter = null;
        entityBackingStore = null;
        parser = null;
        criterionPredicateRegistry = null;

        super.doDestroy();
    }

    /**
     * Subclasses should override this method to perform any initialization logic necessary. Default implementation is a
     * no-op.
     * 
     * @throws ComponentInitializationException thrown if there is a problem initializing the provider
     */
    protected void initMetadataResolver() throws ComponentInitializationException {
        if (getCriterionPredicateRegistry() == null && isUseDefaultPredicateRegistry()) {
            setCriterionPredicateRegistry(new EntityDescriptorCriterionPredicateRegistry());
        }
    }

    /**
     * Unmarshalls the metadata from the given stream. The stream is closed by this method and the returned metadata
     * released its DOM representation.
     * 
     * @param metadataInput the input reader to the metadata.
     * 
     * @return the unmarshalled metadata
     * 
     * @throws UnmarshallingException thrown if the metadata can no be unmarshalled
     */
    @Nonnull protected XMLObject unmarshallMetadata(@Nonnull final InputStream metadataInput)
            throws UnmarshallingException {

        try {
            if (parser == null) {
                throw new UnmarshallingException("ParserPool is null, can't parse input stream");
            }
            log.trace("{} Parsing retrieved metadata into a DOM object", getLogPrefix());
            final Document mdDocument = parser.parse(metadataInput);

            log.trace("{} Unmarshalling and caching metadata DOM", getLogPrefix());
            final Unmarshaller unmarshaller = getUnmarshallerFactory().getUnmarshaller(mdDocument.getDocumentElement());
            if (unmarshaller == null) {
                final String msg =
                        "No unmarshaller registered for document element "
                                + QNameSupport.getNodeQName(mdDocument.getDocumentElement());
                log.warn("{} " + msg, getLogPrefix());
                throw new UnmarshallingException(msg);
            }
            final XMLObject metadata = unmarshaller.unmarshall(mdDocument.getDocumentElement());
            return metadata;
        } catch (final Exception e) {
            throw new UnmarshallingException(e);
        } finally {
            try {
                metadataInput.close();
            } catch (final IOException e2) {
                log.debug("{} Failed to close input: {}", getLogPrefix(), e2);
            }
        }
    }

    /**
     * Filters the given metadata.
     * 
     * @param metadata the metadata to be filtered
     * 
     * @return the filtered metadata
     * 
     * @throws FilterException thrown if there is an error filtering the metadata
     */
    @Nullable protected XMLObject filterMetadata(@Nullable final XMLObject metadata) throws FilterException {
        if (getMetadataFilter() != null) {
            log.debug("{} Applying metadata filter", getLogPrefix());
            return getMetadataFilter().filter(metadata);
        } else {
            return metadata;
        }
    }

    /**
     * Releases the DOM representation from the metadata object.
     * 
     * @param metadata the metadata object
     */
    protected void releaseMetadataDOM(@Nullable final XMLObject metadata) {
        if (metadata != null) {
            metadata.releaseDOM();
            metadata.releaseChildrenDOM(true);
        }
    }

    /**
     * Returns whether the given descriptor is valid. If valid metadata is not required this method always returns true.
     * 
     * @param descriptor the descriptor to check
     * 
     * @return true if valid metadata is not required or the given descriptor is valid, false otherwise
     */
    protected boolean isValid(@Nullable final XMLObject descriptor) {
        if (descriptor == null) {
            return false;
        }

        if (!isRequireValidMetadata()) {
            return true;
        }

        return SAML2Support.isValid(descriptor);
    }

    /**
     * Get list of descriptors matching an entityID.
     * 
     * @param entityID  entityID to lookup
     * @return  a list of descriptors
     * @throws ResolverException if an error occurs
     */
    @Nonnull @NonnullElements protected List<EntityDescriptor> lookupEntityID(@Nonnull @NotEmpty final String entityID)
            throws ResolverException {
        if (!isInitialized()) {
            throw new ResolverException("Metadata resolver has not been initialized");
        }

        if (Strings.isNullOrEmpty(entityID)) {
            log.debug("{} EntityDescriptor entityID was null or empty, skipping search for it", getLogPrefix());
            return Collections.emptyList();
        }

        final List<EntityDescriptor> descriptors = lookupIndexedEntityID(entityID);
        if (descriptors.isEmpty()) {
            log.debug("{} Metadata backing store does not contain any EntityDescriptors with the ID: {}", 
                    getLogPrefix(), entityID);
            return descriptors;
        }

        final Iterator<EntityDescriptor> entitiesIter = descriptors.iterator();
        while (entitiesIter.hasNext()) {
            final EntityDescriptor descriptor = entitiesIter.next();
            if (!isValid(descriptor)) {
                log.warn("{} Metadata backing store contained an EntityDescriptor with the ID: {}, " 
                        + " but it was no longer valid", getLogPrefix(), entityID);
                entitiesIter.remove();
            }
        }

        return descriptors;
    }

    /**
     * Lookup the specified entityID from the index. The returned list will be a copy of what is stored in the backing
     * index, and is safe to be manipulated by callers.
     * 
     * @param entityID the entityID to lookup
     * 
     * @return list copy of indexed entityID's, may be empty, will never be null
     */
    @Nonnull @NonnullElements protected List<EntityDescriptor> lookupIndexedEntityID(
            @Nonnull @NotEmpty final String entityID) {
        final List<EntityDescriptor> descriptors = getBackingStore().getIndexedDescriptors().get(entityID);
        if (descriptors != null) {
            return new ArrayList<>(descriptors);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Create a new backing store instance for EntityDescriptor data. Subclasses may override to return a more
     * specialized subclass type. Note this method does not make the returned backing store the effective one in use.
     * The caller is responsible for calling {@link #setBackingStore(EntityBackingStore)} to make it the effective
     * instance in use.
     * 
     * @return the new backing store instance
     */
    @Nonnull protected EntityBackingStore createNewBackingStore() {
        return new EntityBackingStore();
    }

    /**
     * Get the EntityDescriptor backing store currently in use by the metadata resolver.
     * 
     * @return the current effective entity backing store
     */
    @Nonnull protected EntityBackingStore getBackingStore() {
        return entityBackingStore;
    }

    /**
     * Set the EntityDescriptor backing store currently in use by the metadata resolver.
     * 
     * @param newBackingStore the new entity backing store
     */
    protected void setBackingStore(@Nonnull final EntityBackingStore newBackingStore) {
        entityBackingStore = Constraint.isNotNull(newBackingStore, "EntityBackingStore may not be null");
    }

    /**
     * Pre-process the specified entity descriptor, updating the specified entity backing store instance as necessary.
     * 
     * @param entityDescriptor the target entity descriptor to process
     * @param backingStore the backing store instance to update
     */
    protected void preProcessEntityDescriptor(@Nonnull final EntityDescriptor entityDescriptor,
            @Nonnull final EntityBackingStore backingStore) {

        backingStore.getOrderedDescriptors().add(entityDescriptor);
        indexEntityDescriptor(entityDescriptor, backingStore);
    }
    
    /**
     * Remove from the backing store all metadata for the entity with the given entity ID.
     * 
     * @param entityID the entity ID of the metadata to remove
     * @param backingStore the backing store instance to update
     */
    protected void removeByEntityID(@Nonnull final String entityID, @Nonnull final EntityBackingStore backingStore) {
        final Map<String, List<EntityDescriptor>> indexedDescriptors = backingStore.getIndexedDescriptors();
        final List<EntityDescriptor> descriptors = indexedDescriptors.get(entityID);
        if (descriptors != null) {
            backingStore.getOrderedDescriptors().removeAll(descriptors);
        }
        indexedDescriptors.remove(entityID);
    }

    /**
     * Index the specified entity descriptor, updating the specified entity backing store instance as necessary.
     * 
     * @param entityDescriptor the target entity descriptor to process
     * @param backingStore the backing store instance to update
     */
    protected void indexEntityDescriptor(@Nonnull final EntityDescriptor entityDescriptor,
            @Nonnull final EntityBackingStore backingStore) {

        final String entityID = StringSupport.trimOrNull(entityDescriptor.getEntityID());
        if (entityID != null) {
            List<EntityDescriptor> entities = backingStore.getIndexedDescriptors().get(entityID);
            if (entities == null) {
                entities = new ArrayList<>();
                backingStore.getIndexedDescriptors().put(entityID, entities);
            } else if (!entities.isEmpty()) {
                log.warn("{} Detected duplicate EntityDescriptor for entityID: {}", getLogPrefix(), entityID);
            }
            entities.add(entityDescriptor);
        }
    }

    /**
     * Pre-process the specified entities descriptor, updating the specified entity backing store instance as necessary.
     * 
     * @param entitiesDescriptor the target entities descriptor to process
     * @param backingStore the backing store instance to update
     */
    protected void preProcessEntitiesDescriptor(@Nonnull final EntitiesDescriptor entitiesDescriptor,
            final EntityBackingStore backingStore) {

        for (final XMLObject child : entitiesDescriptor.getOrderedChildren()) {
            if (child instanceof EntityDescriptor) {
                preProcessEntityDescriptor((EntityDescriptor) child, backingStore);
            } else if (child instanceof EntitiesDescriptor) {
                preProcessEntitiesDescriptor((EntitiesDescriptor) child, backingStore);
            }
        }
    }
    
    /**
     * Filter the supplied candidates by resolving predicates from the supplied criteria and applying
     * the predicates to return a filtered {@link Iterable}.
     * 
     * @param candidates the candidates to evaluate
     * @param criteria the criteria set to evaluate
     * @param onEmptyPredicatesReturnEmpty if true and no predicates are supplied, then return an empty iterable;
     *          otherwise return the original input candidates
     * 
     * @return an iterable of the candidates filtered by the resolved predicates
     * 
     * @throws ResolverException if there is a fatal error during resolution
     */
    protected Iterable<EntityDescriptor> predicateFilterCandidates(@Nonnull final Iterable<EntityDescriptor> candidates,
            @Nonnull final CriteriaSet criteria, final boolean onEmptyPredicatesReturnEmpty)
                    throws ResolverException {
        
        if (!candidates.iterator().hasNext()) {
            log.debug("{} Candidates iteration was empty, nothing to filter via predicates", getLogPrefix());
            return Collections.emptySet();
        }
        
        log.debug("{} Attempting to filter candidate EntityDescriptors via resolved Predicates", getLogPrefix());
        
        final Set<Predicate<EntityDescriptor>> predicates = ResolverSupport.getPredicates(criteria, 
                EvaluableEntityDescriptorCriterion.class, getCriterionPredicateRegistry());
        
        log.trace("{} Resolved {} Predicates: {}", getLogPrefix(), predicates.size(), predicates);
        
        final boolean satisfyAny;
        final SatisfyAnyCriterion satisfyAnyCriterion = criteria.get(SatisfyAnyCriterion.class);
        if (satisfyAnyCriterion  != null) {
            log.trace("{} CriteriaSet contained SatisfyAnyCriterion", getLogPrefix());
            satisfyAny = satisfyAnyCriterion.isSatisfyAny();
        } else {
            log.trace("{} CriteriaSet did NOT contain SatisfyAnyCriterion", getLogPrefix());
            satisfyAny = isSatisfyAnyPredicates();
        }
        
        log.trace("{} Effective satisyAny value: {}", getLogPrefix(), satisfyAny);
        
        final Iterable<EntityDescriptor> result = 
                ResolverSupport.getFilteredIterable(candidates, predicates, satisfyAny, onEmptyPredicatesReturnEmpty);
        if (log.isDebugEnabled()) {
            log.debug("{} After predicate filtering {} EntityDescriptors remain", 
                    getLogPrefix(), Iterables.size(result));
        }
        return result;
    }
    
    /**
     * Return a prefix for logging messages for this component.
     * 
     * @return a string for insertion at the beginning of any log messages
     */
    @Nonnull @NotEmpty protected String getLogPrefix() {
        if (logPrefix == null) {
            logPrefix = String.format("Metadata Resolver %s %s:", getClass().getSimpleName(), getId());
        }
        return logPrefix;
    }

    /**
     * The collection of data which provides the backing store for the processed metadata.
     */
    protected class EntityBackingStore {

        /** Index of entity IDs to their descriptors. */
        private Map<String, List<EntityDescriptor>> indexedDescriptors;

        /** Ordered list of entity descriptors. */
        private List<EntityDescriptor> orderedDescriptors;

        /** Constructor. */
        protected EntityBackingStore() {
            indexedDescriptors = new ConcurrentHashMap<>();
            orderedDescriptors = new ArrayList<>();
        }

        /**
         * Get the entity descriptor index.
         * 
         * @return the entity descriptor index
         */
        @Nonnull public Map<String, List<EntityDescriptor>> getIndexedDescriptors() {
            return indexedDescriptors;
        }

        /**
         * Get the ordered entity descriptor list.
         * 
         * @return the entity descriptor list
         */
        @Nonnull public List<EntityDescriptor> getOrderedDescriptors() {
            return orderedDescriptors;
        }

    }

}