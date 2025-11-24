package dk.digitalidentity.service;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.claimsprovider.ClaimsProviderUtil;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.opensaml.CustomHTTPPostEncoder;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;

@Service
@Slf4j
public class LogoutService {

    @Autowired
    private LoggingUtil loggingUtil;

    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private ServiceProviderFactory serviceProviderFactory;

    @Autowired
    private LogoutRequestService logoutRequestService;

    @Autowired
    private LogoutResponseService logoutResponseService;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private OpenSAMLHelperService samlHelper;

    @Autowired
    private ClaimsProviderUtil nemLoginUtil;

    public void sendLogoutResponse(HttpServletResponse httpServletResponse, LogoutRequest logoutRequest) throws ResponderException, RequesterException {
        // If there is no LogoutRequest on session, show IdP index.
        // this happens when logout is IdP initiated
        if (logoutRequest == null) {
            try {
                auditLogger.logout(sessionHelper.getPerson());
                sessionHelper.invalidateSession();
                httpServletResponse.sendRedirect("/");
                return;
            } catch (IOException e) {
                throw new ResponderException("Kunne ikke vidrestille til forsiden efter logud");
            }
        }

        // Create LogoutResponse
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(logoutRequest.getIssuer().getValue());
        SingleLogoutService logoutEndpoint = serviceProvider.getLogoutResponseEndpoint();

        String destination = StringUtils.hasLength(logoutEndpoint.getResponseLocation()) ? logoutEndpoint.getResponseLocation() : logoutEndpoint.getLocation();
        MessageContext<SAMLObject> messageContext = logoutResponseService.createMessageContextWithLogoutResponse(logoutRequest, destination, logoutEndpoint.getBinding(), serviceProvider);

        // Log to Console and AuditLog
        auditLogger.logoutResponse(sessionHelper.getPerson(), samlHelper.prettyPrint((LogoutResponse) messageContext.getMessage()), true, serviceProvider.getName(null));
        auditLogger.logout(sessionHelper.getPerson());
        loggingUtil.logLogoutResponse((LogoutResponse) messageContext.getMessage(), Constants.OUTGOING);

        // Set RelayState
        SAMLBindingSupport.setRelayState(messageContext, sessionHelper.getRelayState());

        // Logout Response is sent as the last thing after all LogoutRequests so delete the remaining values
        sessionHelper.invalidateSession();

        // Deflating and sending the message
        try {
            sendMessage(httpServletResponse, logoutEndpoint, messageContext);
        } catch (ComponentInitializationException | MessageEncodingException e) {
            throw new ResponderException("Kunne ikke sende logout svar (LogoutResponse)", e);
        }
    }

    public void sendLogoutRequest(HttpServletResponse httpServletResponse, LogoutRequest logoutRequest) throws ResponderException, RequesterException {
        Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();

        Iterator<Map.Entry<String, Map<String, String>>> iterator = spSessions.entrySet().iterator();

        if (iterator.hasNext()) {
            Map.Entry<String, Map<String, String>> next = iterator.next();

            // Create LogoutRequest
            ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(next.getKey());
            SingleLogoutService logoutEndpoint = serviceProvider.getLogoutEndpoint();
            MessageContext<SAMLObject> messageContext = logoutRequestService.createMessageContextWithLogoutRequest(logoutRequest, logoutEndpoint.getLocation(), serviceProvider);

            // Log to Console and AuditLog
            auditLogger.logoutRequest(sessionHelper.getPerson(), samlHelper.prettyPrint((LogoutRequest) messageContext.getMessage()), true, serviceProvider.getName(null));
            loggingUtil.logLogoutRequest((LogoutRequest) messageContext.getMessage(), Constants.OUTGOING);

            // Send LogoutRequest
            try {
                sendMessage(httpServletResponse, logoutEndpoint, messageContext);
            } catch (ComponentInitializationException | MessageEncodingException e) {
                throw new ResponderException("Kunne ikke sende logout forespørgsel (LogoutRequest)", e);
            }

            // Remove ServiceProvider from session
            spSessions.remove(next.getKey());
        }
        sessionHelper.setServiceProviderSessions(spSessions);
    }

    public void sendMessage(HttpServletResponse httpServletResponse, SingleLogoutService logoutEndpoint, MessageContext<SAMLObject> message) throws ComponentInitializationException, MessageEncodingException {
        if (SAMLConstants.SAML2_POST_BINDING_URI.equals(logoutEndpoint.getBinding())) {
            CustomHTTPPostEncoder encoder = new CustomHTTPPostEncoder();

            encoder.setHttpServletResponse(httpServletResponse);
            encoder.setMessageContext(message);
            encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

            encoder.initialize();
            encoder.encode();
        }
        else if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(logoutEndpoint.getBinding())) {
            HTTPRedirectDeflateEncoder encoder = new HTTPRedirectDeflateEncoder();

            encoder.setMessageContext(message);
            encoder.setHttpServletResponse(httpServletResponse);

            encoder.initialize();
            encoder.encode();
        }
    }

    public void logout(HttpServletResponse response, LogoutRequest logoutRequest, Map<String, Map<String, String>> spSessions) throws ResponderException, RequesterException {
        // Delete session and save logoutRequest
        sessionHelper.logout(logoutRequest);

        // Either send Response or send new request to a remaining service provider
        // Sends a LogoutRequest to the next remaining ServiceProvider
        // When all ServiceProviders have been logged out, we check for our NemLog-in integration, and log it out if necessary
        // When all ServiceProviders AND NemLog-in is logged out, send response to original requesting ServiceProvider
        if (spSessions.keySet().size() > 0) {
            sendLogoutRequest(response, logoutRequest);
        }
        else if (nemLoginUtil.isAuthenticated()) {
            try {
                response.sendRedirect("/nemlogin/saml/logout");
            }
            catch (IOException e) {
                log.warn("Kunne ikke logge ud af NemLog-in: " + e.getMessage());

                sendLogoutResponse(response, logoutRequest);
            }
        }
        else {
            sendLogoutResponse(response, logoutRequest);
        }
    }
}
