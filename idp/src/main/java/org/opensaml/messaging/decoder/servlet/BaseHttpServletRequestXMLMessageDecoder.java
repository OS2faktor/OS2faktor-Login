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

package org.opensaml.messaging.decoder.servlet;

import java.io.InputStream;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.logic.Constraint;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import net.shibboleth.utilities.java.support.xml.XMLParserException;

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.messaging.decoder.MessageDecoder;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

// OBS! Modified this from OpenSAML Messaging API 3.4.6, to change error logs to warn logs (see OBS comments below)

/**
 * Base class for message decoders which decode XML messages from an {@link javax.servlet.http.HttpServletRequest}.
 * 
 * @param <MessageType> the message type of the message context on which to operate
 */
public abstract class BaseHttpServletRequestXMLMessageDecoder<MessageType extends XMLObject>
    extends AbstractHttpServletRequestMessageDecoder<MessageType> {
    
    /** Used to log protocol messages. */
    private Logger protocolMessageLog = LoggerFactory.getLogger("PROTOCOL_MESSAGE");

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(BaseHttpServletRequestXMLMessageDecoder.class);

    /** Parser pool used to deserialize the message. */
    private ParserPool parserPool;

    /** Constructor. */
    public BaseHttpServletRequestXMLMessageDecoder() {
        parserPool = XMLObjectProviderRegistrySupport.getParserPool();
    }

    /** {@inheritDoc} */
    public void decode() throws MessageDecodingException {
        log.debug("Beginning to decode message from HttpServletRequest");
        
        log.debug("HttpServletRequest indicated Content-Type: {}", getHttpServletRequest().getContentType());
        
        validateHttpRequest(getHttpServletRequest());
        
        super.decode();
        
        logDecodedMessage();

        log.debug("Successfully decoded message from HttpServletRequest.");
    }
    
    /**
     * Gets the parser pool used to deserialize incoming messages.
     * 
     * @return parser pool used to deserialize incoming messages
     */
    @Nonnull public ParserPool getParserPool() {
        return parserPool;
    }

    /**
     * Sets the parser pool used to deserialize incoming messages.
     * 
     * @param pool parser pool used to deserialize incoming messages
     */
    public void setParserPool(@Nonnull final ParserPool pool) {
        Constraint.isNotNull(pool, "ParserPool cannot be null");
        parserPool = pool;
    }
    
    /** {@inheritDoc} */
    protected void doDestroy() {
        parserPool = null;
        
        super.doDestroy();
    }
    
    /** {@inheritDoc} */
    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();
        
        if (parserPool == null) {
            throw new ComponentInitializationException("Parser pool cannot be null");
        }
    }

    /**
     * Log the decoded message to the protocol message logger.
     */
    protected void logDecodedMessage() {
        if (protocolMessageLog.isDebugEnabled() ){
            final XMLObject message = getMessageToLog();
            if (message == null) {
                log.warn("Decoded message was null, nothing to log");
                return;
            }
            
            try {
                final Element dom = XMLObjectSupport.marshall(message);
                protocolMessageLog.debug("\n" + SerializeSupport.prettyPrintXML(dom));
            } catch (final MarshallingException e) {
                log.error("Unable to marshall message for logging purposes", e);
            }
        }
    }
    
    /**
     * Get the XMLObject which will be logged as the protocol message.
     * 
     * @return the XMLObject message considered to be the protocol message for logging purposes
     */
    protected XMLObject getMessageToLog() {
        return getMessageContext().getMessage();
    }

    /**
     * Helper method that deserializes and unmarshalls the message from the given stream.
     * 
     * @param messageStream input stream containing the message
     * 
     * @return the inbound message
     * 
     * @throws MessageDecodingException thrown if there is a problem deserializing and unmarshalling the message
     */
    protected XMLObject unmarshallMessage(final InputStream messageStream) throws MessageDecodingException {
        try {
            final XMLObject message = XMLObjectSupport.unmarshallFromInputStream(getParserPool(), messageStream);
            return message;
        } catch (final XMLParserException e) {
        	// OBS! changed from error to warn
            log.warn("Error unmarshalling message from input stream", e);
            throw new MessageDecodingException("Error unmarshalling message from input stream", e);
        } catch (final UnmarshallingException e) {
        	// OBS! changed from error to warn
            log.warn("Error unmarshalling message from input stream", e);
            throw new MessageDecodingException("Error unmarshalling message from input stream", e);
        }
    }
    
    /**
     * Perform optional validation of the inbound {@link HttpServletRequest}.
     * 
     * <p>
     * This method is called before the main {@link MessageDecoder#decode()} logic}.
     * </p>
     * 
     * <p>
     * The default behavior is a no-op.  Subclasses may override with specific constraints.
     * </p>
     * 
     * @param request the HTTP request being validate
     * 
     * @throws MessageDecodingException if request is not considered valid
     */
    protected void validateHttpRequest(final HttpServletRequest request) throws MessageDecodingException {
        // Default is no-op
    }

}