package dk.digitalidentity.opensaml;

import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.ClientAbortException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;

import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.net.HttpServletSupport;

@Slf4j
public class CustomHTTPPostEncoder extends HTTPPostEncoder {
	private VelocityEngine _engine = null;
	
	// carbon copy of OpenSAML 3.4.6 version of this method, minus the log.error (which is now a log.warn to avoid alarms)
	@Override
	protected void postEncode(MessageContext<SAMLObject> messageContext, String endpointURL) throws MessageEncodingException {
        log.debug("Invoking Velocity template to create POST body");
        try {
            final VelocityContext context = new VelocityContext();

            populateVelocityContext(context, messageContext, endpointURL);

            final HttpServletResponse response = getHttpServletResponse();
            
            HttpServletSupport.addNoCacheHeaders(response);
            HttpServletSupport.setUTF8Encoding(response);
            HttpServletSupport.setContentType(response, "text/html");
            
            final Writer out = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
            _engine.mergeTemplate(HTTPPostEncoder.DEFAULT_TEMPLATE_ID, "UTF-8", context, out);
            out.flush();
        }
        catch (final Exception e) {
        	// this is the change
        	if (e instanceof ClientAbortException || e instanceof VelocityException) {
        		log.warn("Error invoking Velocity template", e);
        	}
        	else {
        		log.error("Error generating POST response", e);
        	}

            throw new MessageEncodingException("Error creating output document", e);
        }
	}
	
	@Override
	public void setVelocityEngine(VelocityEngine newVelocityEngine) {
		super.setVelocityEngine(newVelocityEngine);

		_engine = newVelocityEngine;
	}
}
