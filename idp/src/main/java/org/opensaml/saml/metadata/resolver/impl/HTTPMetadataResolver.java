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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;

import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.primitive.DeprecationSupport;
import net.shibboleth.utilities.java.support.primitive.DeprecationSupport.ObjectType;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.util.EntityUtils;
import org.opensaml.security.httpclient.HttpClientSecurityConstants;
import org.opensaml.security.httpclient.HttpClientSecurityParameters;
import org.opensaml.security.httpclient.HttpClientSecuritySupport;
import org.opensaml.security.trust.TrustEngine;
import org.opensaml.security.x509.X509Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A metadata provider that pulls metadata using an HTTP GET. Metadata is cached until one of these criteria is met:
 * <ul>
 * <li>The smallest cacheDuration within the metadata is exceeded</li>
 * <li>The earliest validUntil time within the metadata is exceeded</li>
 * <li>The maximum cache duration is exceeded</li>
 * </ul>
 * 
 * Metadata is filtered prior to determining the cache expiration data. This allows a filter to remove XMLObjects that
 * may effect the cache duration but for which the user of this provider does not care about.
 * 
 * It is the responsibility of the caller to re-initialize, via {@link #initialize()}, if any properties of this
 * provider are changed.
 */
public class HTTPMetadataResolver extends AbstractReloadingMetadataResolver {
	// CHANGE: added a map to keep track of errors
	private static Map<String, Long> updateErrorCounter = new HashMap<>();

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(HTTPMetadataResolver.class);

    /** HTTP Client used to pull the metadata. */
    private HttpClient httpClient;

    /** URL to the Metadata. */
    private URI metadataURI;

    /** The ETag provided when the currently cached metadata was fetched. */
    private String cachedMetadataETag;

    /** The Last-Modified information provided when the currently cached metadata was fetched. */
    private String cachedMetadataLastModified;

    /** HttpClient credentials provider. 
     * @deprecated use {@link #httpClientSecurityParameters}.
     * */
    @Nullable private BasicCredentialsProvider credentialsProvider;
    
    /** Optional trust engine used in evaluating server TLS credentials. 
     * @deprecated use {@link #httpClientSecurityParameters}.
     * */
    @Nullable private TrustEngine<? super X509Credential> tlsTrustEngine;
    
    /** Optional HttpClient security parameters.*/
    @Nullable private HttpClientSecurityParameters httpClientSecurityParameters;

    /**
     * Constructor.
     * 
     * @param client HTTP client used to pull in remote metadata
     * @param metadataURL URL to the remove remote metadata
     * 
     * @throws ResolverException thrown if the HTTP client is null or the metadata URL provided is invalid
     */
    public HTTPMetadataResolver(final HttpClient client, final String metadataURL) throws ResolverException {
        this(null, client, metadataURL);
    }

    /**
     * Constructor.
     * 
     * @param backgroundTaskTimer timer used to schedule background metadata refresh tasks
     * @param client HTTP client used to pull in remote metadata
     * @param metadataURL URL to the remove remote metadata
     * 
     * @throws ResolverException thrown if the HTTP client is null or the metadata URL provided is invalid
     */
    public HTTPMetadataResolver(final Timer backgroundTaskTimer, final HttpClient client, final String metadataURL)
            throws ResolverException {
        super(backgroundTaskTimer);

        if (client == null) {
            throw new ResolverException("HTTP client may not be null");
        }
        httpClient = client;

        try {
            metadataURI = new URI(metadataURL);
        } catch (final URISyntaxException e) {
            throw new ResolverException("Illegal URL syntax", e);
        }
    }

    /**
     * Gets the URL to fetch the metadata.
     * 
     * @return the URL to fetch the metadata
     */
    public String getMetadataURI() {
        return metadataURI.toASCIIString();
    }
    
    /**
     * Sets the optional trust engine used in evaluating server TLS credentials.
     * 
     * <p>
     * See TLS socket factory requirements documented for 
     * {@link #setHttpClientSecurityParameters(HttpClientSecurityParameters)}.
     * </p>
     * 
     * @param engine the trust engine instance to use
     * 
     * @deprecated use {@link #setHttpClientSecurityParameters(HttpClientSecurityParameters)}
     */
    public void setTLSTrustEngine(@Nullable final TrustEngine<? super X509Credential> engine) {
        DeprecationSupport.warnOnce(ObjectType.METHOD, getClass().getName() + ".setTLSTrustEngine", 
                null, "setHttpClientSecurityParameters(HttpClientSecurityParameters)");
        tlsTrustEngine = engine;
    }

    /**
     * Sets the username and password used to access the metadata URL. To disable BASIC authentication pass null for the
     * credentials instance.
     * 
     * An {@link AuthScope} will be generated based off the metadata URI's hostname and port.
     * 
     * @param credentials the username and password credentials
     * 
     * @deprecated use {@link #setHttpClientSecurityParameters(HttpClientSecurityParameters)}
     */
    public void setBasicCredentials(@Nullable final UsernamePasswordCredentials credentials) {
        DeprecationSupport.warnOnce(ObjectType.METHOD, getClass().getName() + ".setBasicCredentials", 
                null, "setHttpClientSecurityParameters(HttpClientSecurityParameters)");
        setBasicCredentialsWithScope(credentials, null);
    }

    /**
     * Sets the username and password used to access the metadata URL. To disable BASIC authentication pass null for the
     * credentials instance.
     * 
     * <p>
     * If the <code>authScope</code> is null, an {@link AuthScope} will be generated based off the metadata URI's
     * hostname and port.
     * </p>
     * 
     * @param credentials the username and password credentials
     * @param scope the HTTP client auth scope with which to scope the credentials, may be null
     * 
     * @deprecated use {@link #setHttpClientSecurityParameters(HttpClientSecurityParameters)}
     */
    public void setBasicCredentialsWithScope(@Nullable final UsernamePasswordCredentials credentials,
            @Nullable final AuthScope scope) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        
        DeprecationSupport.warnOnce(ObjectType.METHOD, getClass().getName() + ".setBasicCredentialsWithScope", 
                null, "setHttpClientSecurityParameters(HttpClientSecurityParameters)");

        if (credentials != null) {
            AuthScope authScope = scope;
            if (authScope == null) {
                authScope = new AuthScope(metadataURI.getHost(), metadataURI.getPort());
            }
            final BasicCredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(authScope, credentials);
            credentialsProvider = provider;
        } else {
            log.debug("Either username or password were null, disabling basic auth");
            credentialsProvider = null;
        }

    }
    
    /**
     * Get the instance of {@link HttpClientSecurityParameters} which provides various parameters to influence
     * the security behavior of the HttpClient instance.
     * 
     * @return the parameters instance, or null
     */
    @Nullable protected HttpClientSecurityParameters getHttpClientSecurityParameters() {
        return httpClientSecurityParameters;
    }
    
    /**
     * Set an instance of {@link HttpClientSecurityParameters} which provides various parameters to influence
     * the security behavior of the HttpClient instance.
     * 
     * <p>
     * For all TLS-related parameters, must be used in conjunction with an HttpClient instance 
     * which is configured with either a:
     * <ul>
     * <li>
     * a {@link net.shibboleth.utilities.java.support.httpclient.TLSSocketFactory}
     * </li>
     * <li>
     * {@link org.opensaml.security.httpclient.impl.SecurityEnhancedTLSSocketFactory} which wraps
     * an instance of {@link net.shibboleth.utilities.java.support.httpclient.TLSSocketFactory}, with
     * the latter likely configured in a "no trust" configuration.  This variant is required if either a
     * trust engine or a client TLS credential is to be used.
     * </li>
     * For convenience methods for building a 
     * {@link net.shibboleth.utilities.java.support.httpclient.TLSSocketFactory}, 
     * see {@link net.shibboleth.utilities.java.support.httpclient.HttpClientSupport}.
     * </ul>
     * If the appropriate TLS socket factory is not configured and a trust engine is specified, 
     * then this will result in no TLS trust evaluation being performed and a 
     * {@link ResolverException} will ultimately be thrown.
     * </p>
     * @param params the security parameters
     */
    public void setHttpClientSecurityParameters(@Nullable final HttpClientSecurityParameters params) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);

        httpClientSecurityParameters = params;
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        httpClient = null;
        tlsTrustEngine = null;
        credentialsProvider = null;
        httpClientSecurityParameters = null;
        metadataURI = null;
        cachedMetadataETag = null;
        cachedMetadataLastModified = null;

        super.doDestroy();
    }

    /** {@inheritDoc} */
    @Override
    protected String getMetadataIdentifier() {
        return metadataURI.toString();
    }

    /**
     * Gets the metadata document from the remote server.
     * 
     * @return the metadata from remote server, or null if the metadata document has not changed since the last
     *         retrieval
     * 
     * @throws ResolverException thrown if there is a problem retrieving the metadata from the remote server
     */
    @Override
    protected byte[] fetchMetadata() throws ResolverException {
        final HttpGet httpGet = buildHttpGet();
        final HttpClientContext context = buildHttpClientContext(httpGet);
        HttpResponse response = null;

        try {
            log.debug("{} Attempting to fetch metadata document from '{}'", getLogPrefix(), metadataURI);
            response = httpClient.execute(httpGet, context);
            HttpClientSecuritySupport.checkTLSCredentialEvaluated(context, metadataURI.getScheme());
            final int httpStatusCode = response.getStatusLine().getStatusCode();

            if (httpStatusCode == HttpStatus.SC_NOT_MODIFIED) {
                log.debug("{} Metadata document from '{}' has not changed since last retrieval", 
                        getLogPrefix(), getMetadataURI());
                return null;
            }

            if (httpStatusCode != HttpStatus.SC_OK) {
                final String errMsg =
                        "Non-ok status code " + httpStatusCode + " returned from remote metadata source " + metadataURI;
                log.warn("{} " + errMsg, getLogPrefix());
                throw new IOException(errMsg);
            }

            processConditionalRetrievalHeaders(response);

            final byte[] rawMetadata = getMetadataBytesFromResponse(response);
            log.debug("{} Successfully fetched {} bytes of metadata from {}", 
                    getLogPrefix(), rawMetadata.length, getMetadataURI());

            // CHANGE: added a counter that is reset on success
            // all is well, reset errorCounter
        	updateErrorCounter.put(getMetadataURI(), 0L);
            
            return rawMetadata;
        } catch (final IOException e) {
        	// CHANGE: refactored this to log warnings for first 5 errors in a row
        	Long counter = updateErrorCounter.get(getMetadataURI());
        	if (counter == null) {
        		counter = 1L;
        	}
        	else {
        		counter = counter + 1;
        	}

        	final String errMsg = "Error retrieving metadata from " + metadataURI;
        	
        	if (counter >= 5) {
                log.error("{} " + errMsg, getLogPrefix(), e);        		
        	}
        	else {
        		log.warn("{} " + errMsg, getLogPrefix(), e);
        	}

            throw new ResolverException(errMsg, e);
        } finally {
            try {
                if (response != null && response instanceof CloseableHttpResponse) {
                    ((CloseableHttpResponse) response).close();
                }
            } catch (final IOException e) {
                log.error("{} Error closing HTTP response from {}", metadataURI, getLogPrefix(), e);
            }
        }
    }

    /**
     * Check that trust engine evaluation of the server TLS credential was actually performed.
     * 
     * @param context the current HTTP context instance in use
     * @throws SSLPeerUnverifiedException thrown if the TLS credential was not actually evaluated by the trust engine
     * 
     * @deprecated use {@link HttpClientSecuritySupport#checkTLSCredentialEvaluated(HttpClientContext, String)}
     */
    @Deprecated
    protected void checkTLSCredentialTrusted(final HttpClientContext context) throws SSLPeerUnverifiedException {
        DeprecationSupport.warnOnce(ObjectType.METHOD, getClass().getName()+ ".checkTLSCredentialTrusted", 
                null, "HttpClientSecuritySupport.checkTLSCredentialEvaluated(..)");
        HttpClientSecuritySupport.checkTLSCredentialEvaluated(context, metadataURI.getScheme());
    }

    /**
     * Builds the {@link HttpGet} instance used to fetch the metadata. The returned method advertises support for GZIP
     * and deflate compression, enables conditional GETs if the cached metadata came with either an ETag or
     * Last-Modified information, and sets up basic authentication if such is configured.
     * 
     * @return the constructed HttpGet instance
     */
    protected HttpGet buildHttpGet() {
        final HttpGet getMethod = new HttpGet(getMetadataURI());

        if (cachedMetadataETag != null) {
            getMethod.setHeader("If-None-Match", cachedMetadataETag);
        }
        if (cachedMetadataLastModified != null) {
            getMethod.setHeader("If-Modified-Since", cachedMetadataLastModified);
        }

        return getMethod;
    }

    /**
     * Build the {@link HttpClientContext} instance which will be used to invoke the {@link HttpClient} request.
     * 
     * @return a new instance of {@link HttpClientContext}
     * 
     * @deprecated use {@link #buildHttpClientContext(HttpUriRequest)}
     */
    protected HttpClientContext buildHttpClientContext() {
        //TODO when we remove this deprecated method, change called method to @Nonnull for request
        DeprecationSupport.warn(ObjectType.METHOD, getClass().getName() + ".buildHttpClientContext()", null, null);
        return buildHttpClientContext(null);
    }
    
    /**
     * Build the {@link HttpClientContext} instance which will be used to invoke the {@link HttpClient} request.
     * 
     * @param request the current HTTP request
     * 
     * @return a new instance of {@link HttpClientContext}
     */
    protected HttpClientContext buildHttpClientContext(@Nullable final HttpUriRequest request) {
        // TODO Really request should be @Nonnull, change when we remove deprecated buildHttpClientContext()
        final HttpClientContext context = HttpClientContext.create();
        
        HttpClientSecuritySupport.marshalSecurityParameters(context, httpClientSecurityParameters, true);
        
        // If these legacy values are present, let them override the above params instance values unconditionally
        if (credentialsProvider != null) {
            context.setCredentialsProvider(credentialsProvider);
        }
        if (tlsTrustEngine != null) {
            context.setAttribute(HttpClientSecurityConstants.CONTEXT_KEY_TRUST_ENGINE, tlsTrustEngine);
        }
        
        if (request != null) {
            HttpClientSecuritySupport.addDefaultTLSTrustEngineCriteria(context, request);
        }
        
        return context;
    }

    /**
     * Records the ETag and Last-Modified headers, from the response, if they are present.
     * 
     * @param response GetMethod containing a valid HTTP response
     */
    protected void processConditionalRetrievalHeaders(final HttpResponse response) {
        Header httpHeader = response.getFirstHeader("ETag");
        if (httpHeader != null) {
            cachedMetadataETag = httpHeader.getValue();
        }

        httpHeader = response.getFirstHeader("Last-Modified");
        if (httpHeader != null) {
            cachedMetadataLastModified = httpHeader.getValue();
        }
    }

    /**
     * Extracts the raw metadata bytes from the response taking in to account possible deflate and GZip compression.
     * 
     * @param response GetMethod containing a valid HTTP response
     * 
     * @return the raw metadata bytes
     * 
     * @throws ResolverException thrown if there is a problem getting the raw metadata bytes from the response
     */
    protected byte[] getMetadataBytesFromResponse(final HttpResponse response) throws ResolverException {
        log.debug("{} Attempting to extract metadata from response to request for metadata from '{}'", 
                getLogPrefix(), getMetadataURI());
        try {
            final InputStream ins = response.getEntity().getContent();
            return inputstreamToByteArray(ins);
        } catch (final IOException e) {
            log.error("{} Unable to read response", getLogPrefix(), e);
            throw new ResolverException("Unable to read response", e);
        } finally {
            // Make sure entity has been completely consumed.
            EntityUtils.consumeQuietly(response.getEntity());
        }
    }
}