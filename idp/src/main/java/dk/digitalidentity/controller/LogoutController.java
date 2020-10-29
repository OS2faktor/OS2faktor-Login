package dk.digitalidentity.controller;

import java.security.PublicKey;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.security.credential.UsageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LogoutRequestService;
import dk.digitalidentity.service.LogoutResponseService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;

@Slf4j
@Controller
public class LogoutController {

    @Autowired
    private ErrorResponseService errorResponseService;

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

    @GetMapping("/sso/saml/logout")
    public String logoutRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
        MessageContext<SAMLObject> messageContext = null;
        ServiceProvider serviceProvider = null;
        LogoutRequest logoutRequest = null;
        try {
            messageContext = logoutRequestService.getMessageContext(httpServletRequest);
            logoutRequest = logoutRequestService.getLogoutRequest(messageContext);
            loggingUtil.logLogoutRequest(logoutRequest, Constants.INCOMING);

            serviceProvider = serviceProviderFactory.getServiceProvider(logoutRequest.getIssuer().getValue());
        } catch (RequesterException | ResponderException e) {
            log.error("Error occurred, no destination to send error known", e);
            return null;
        }

        try {
            // Validate logout request
            EntityDescriptor spMetadata = serviceProvider.getMetadata();
            PublicKey spKey = serviceProvider.getPublicKey(UsageType.SIGNING);
            logoutRequestService.validateLogoutRequest(httpServletRequest, messageContext, spMetadata, spKey);

            // Delete session
            sessionHelper.setPasswordLevel(null);
            sessionHelper.setMFALevel(null);
            sessionHelper.setLogoutRequest(logoutRequest);

            // Remove the EntityId of the SP sending the LogoutRequest since the user is no longer logged in there
            Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
            spSessions.remove(serviceProvider.getEntityId());
            sessionHelper.setServiceProviderSessions(spSessions);

            // Either send Response or send new request to a remaining service provider
            if (spSessions.keySet().size() > 0) {
                sendLogoutRequest(httpServletResponse, logoutRequest);
            } else {
                sendLogoutResponse(httpServletResponse, logoutRequest);
            }
        } catch (RequesterException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = !StringUtils.isEmpty(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();

            errorResponseService.sendError(httpServletResponse, destination, logoutRequest.getID(), StatusCode.REQUESTER, ex);
        } catch (ResponderException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = !StringUtils.isEmpty(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
            errorResponseService.sendError(httpServletResponse, destination, logoutRequest.getID(), StatusCode.RESPONDER, ex);
        }
        return null;
    }

    @GetMapping("/sso/saml/logout/response")
    public void logoutResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
        MessageContext<SAMLObject> messageContext;
        LogoutResponse logoutResponse;
        ServiceProvider serviceProvider = null;
        try {
            messageContext = logoutResponseService.getMessageContext(httpServletRequest);
            logoutResponse = logoutResponseService.getLogoutResponse(messageContext);
            loggingUtil.logLogoutResponse(logoutResponse, Constants.INCOMING);

            serviceProvider = serviceProviderFactory.getServiceProvider(logoutResponse.getIssuer().getValue());
        } catch (RequesterException | ResponderException e) {
            log.error("Error occurred, no destination to send error known", e);
        }

        // Get logoutRequest that started logout
        LogoutRequest logoutRequest = null;
        try {
            logoutRequest = sessionHelper.getLogoutRequest();

            // Either send Response or send new request to a remaining service provider
            if (sessionHelper.getServiceProviderSessions().keySet().size() > 0) {
                sendLogoutRequest(httpServletResponse, logoutRequest);
            } else {
                sendLogoutResponse(httpServletResponse, logoutRequest);
            }
        } catch (ResponderException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = !StringUtils.isEmpty(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
            errorResponseService.sendError(httpServletResponse, destination, logoutRequest.getID(), StatusCode.RESPONDER, ex);
        } catch (RequesterException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = !StringUtils.isEmpty(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
            errorResponseService.sendError(httpServletResponse, destination, logoutRequest.getID(), StatusCode.REQUESTER, ex);
        }
    }

    private void sendLogoutResponse(HttpServletResponse httpServletResponse, LogoutRequest logoutRequest) throws ResponderException, RequesterException {
        sessionHelper.setPerson(null);

        // Create LogoutResponse
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(logoutRequest.getIssuer().getValue());
        SingleLogoutService logoutEndpoint = serviceProvider.getLogoutResponseEndpoint();

        String destination = !StringUtils.isEmpty(logoutEndpoint.getResponseLocation()) ? logoutEndpoint.getResponseLocation() : logoutEndpoint.getLocation();
        MessageContext<SAMLObject> messageContext = logoutResponseService.createMessageContextWithLogoutResponse(logoutRequest, destination);

        // Deflating and sending the message
        try {
            sendMessage(httpServletResponse, logoutEndpoint, messageContext);
        } catch (ComponentInitializationException | MessageEncodingException e) {
            throw new ResponderException("Kunne ikke sende logout svar (LogoutResponse)", e);
        }
    }

    private void sendLogoutRequest(HttpServletResponse httpServletResponse, LogoutRequest logoutRequest) throws ResponderException, RequesterException {
        Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();

        Iterator<Map.Entry<String, Map<String, String>>> iterator = spSessions.entrySet().iterator();

        if (iterator.hasNext()) {
            Map.Entry<String, Map<String, String>> next = iterator.next();

            // Create LogoutRequest
            ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(next.getKey());
            SingleLogoutService logoutEndpoint = serviceProvider.getLogoutEndpoint();
            MessageContext<SAMLObject> messageContext = logoutRequestService.createMessageContextWithLogoutRequest(logoutRequest, logoutEndpoint.getLocation(), serviceProvider);

            // Send LogoutRequest
            try {
                sendMessage(httpServletResponse, logoutEndpoint, messageContext);
            } catch (ComponentInitializationException | MessageEncodingException e) {
                throw new ResponderException("Kunne ikke sende logout forespørgsel (LogoutRequest)", e);
            }

            // Log LogoutRequest and remove ServiceProvider from session
            loggingUtil.logLogoutRequest((LogoutRequest) messageContext.getMessage(), Constants.OUTGOING);
            spSessions.remove(next.getKey());
        }
        sessionHelper.setServiceProviderSessions(spSessions);
    }

    private void sendMessage(HttpServletResponse httpServletResponse, SingleLogoutService logoutEndpoint, MessageContext<SAMLObject> message) throws ComponentInitializationException, MessageEncodingException {
        if (SAMLConstants.SAML2_POST_BINDING_URI.equals(logoutEndpoint.getBinding())) {
            HTTPRedirectDeflateEncoder encoder = new HTTPRedirectDeflateEncoder();

            encoder.setMessageContext(message);
            encoder.setHttpServletResponse(httpServletResponse);

            encoder.initialize();
            encoder.encode();
        } else if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(logoutEndpoint.getBinding())) {
            HTTPPostEncoder encoder = new HTTPPostEncoder();

            encoder.setHttpServletResponse(httpServletResponse);
            encoder.setMessageContext(message);
            encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

            encoder.initialize();
            encoder.encode();
        }
    }
}
