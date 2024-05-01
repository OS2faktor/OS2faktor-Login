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

package net.shibboleth.utilities.java.support.xml;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import net.shibboleth.utilities.java.support.logic.Constraint;

// CHANGED! This is from java-support.7.5.2, and the only modification is changing ERROR logs to WARN

/**
 * A SAX error handler that logs errors to a {@link Logger} before rethrowing them.
 */
public final class LoggingErrorHandler implements ErrorHandler {

    /** Error logger. */
    @Nonnull private Logger log;
    
    /** Whether to pass exception to logger. */
    private boolean logException;

    /**
     * Constructor.
     * 
     * @param logger logger errors will be written to
     */
    public LoggingErrorHandler(@Nonnull final Logger logger) {
        log = Constraint.isNotNull(logger, "Logger cannot be null");
        logException = false;
    }
    
    /**
     * Set whether to log the exception or just a message.
     * 
     * @param flag flag to set
     */
    public void setLogException(final boolean flag) {
        logException = flag;
    }
    

    /** {@inheritDoc} */
    public void error(final SAXParseException exception) throws SAXException {
        if (logException) {
            log.warn("XML Parsing Error", exception);
        } else {
            log.warn("XML Parsing Error");
        }
        throw exception;
    }

    /** {@inheritDoc} */
    public void fatalError(final SAXParseException exception) throws SAXException {
        if (logException) {
            log.warn("XML Parsing Error", exception);
        } else {
            log.warn("XML Parsing Error");
        }
        throw exception;
    }

    /** {@inheritDoc} */
    public void warning(final SAXParseException exception) throws SAXException {
        if (logException) {
            log.warn("XML Parsing Error", exception);
        } else {
            log.warn("XML Parsing Error");
        }
        throw exception;
    }
}