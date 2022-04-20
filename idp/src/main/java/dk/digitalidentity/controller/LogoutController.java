package dk.digitalidentity.controller;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.nemlogin.NemLoginUtil;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LogoutRequestService;
import dk.digitalidentity.service.LogoutResponseService;
import dk.digitalidentity.service.OpenSAMLHelperService;
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

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private OpenSAMLHelperService samlHelper;

    @Autowired
    private NemLoginUtil nemLoginUtil;

    // user initiated logout from IdP UI
    @GetMapping("/sso/saml/logoutIdP")
    public String logoutIdp(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();

        try {
        	logout(httpServletResponse, null, spSessions);
        	return null;
        }
        catch (Exception ex) {
        	log.warn("Failed to perform SLO", ex);
        }

    	return "redirect:/";
    }

    @GetMapping("/sso/saml/logout")
    public String logoutRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
        MessageContext<SAMLObject> messageContext = null;
        ServiceProvider serviceProvider = null;
        LogoutRequest logoutRequest = null;
        try {
            messageContext = logoutRequestService.getMessageContext(httpServletRequest);
            logoutRequest = logoutRequestService.getLogoutRequest(messageContext);

            // Log to Console and AuditLog
            loggingUtil.logLogoutRequest(logoutRequest, Constants.INCOMING);
            serviceProvider = serviceProviderFactory.getServiceProvider(logoutRequest.getIssuer().getValue());
            auditLogger.logoutRequest(sessionHelper.getPerson(), samlHelper.prettyPrint(logoutRequest), false, serviceProvider.getName(null));

            // Save RelayState
            SAMLBindingContext subcontext = messageContext.getSubcontext(SAMLBindingContext.class);
            String relayState = subcontext != null ? subcontext.getRelayState() : null;
            sessionHelper.setRelayState(relayState);

		}
		catch (RequesterException | ResponderException e) {
			log.error("Error occurred, no destination to send error known", e);
			return null;
		}

        try {
            // Validate logout request
            EntityDescriptor spMetadata = serviceProvider.getMetadata();
            PublicKey spKey = serviceProvider.getSigningKey();
            logoutRequestService.validateLogoutRequest(httpServletRequest, messageContext, spMetadata, spKey);

            // Remove the EntityId of the SP sending the LogoutRequest since the user is no longer logged in there
            Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
            spSessions.remove(serviceProvider.getEntityId());
            sessionHelper.setServiceProviderSessions(spSessions);

            // TODO: virker forkert... bør logout requestet ikke komme fra IdP'en når den nu sendes til de andre SP'ere?
            // bør testes med 2 SP'ere for at se hvordan logout requestet ser ud når det sendes videre til næste IdP
            logout(httpServletResponse, logoutRequest, spSessions);
		}
		catch (RequesterException ex) {
			SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
			String destination = StringUtils.hasLength(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();

			errorResponseService.sendError(httpServletResponse, destination, logoutRequest.getID(), StatusCode.REQUESTER, ex);
		}
		catch (ResponderException ex) {
			SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
			String destination = StringUtils.hasLength(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
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

            // Log to Console and AuditLog
            loggingUtil.logLogoutResponse(logoutResponse, Constants.INCOMING);
            serviceProvider = serviceProviderFactory.getServiceProvider(logoutResponse.getIssuer().getValue());
            auditLogger.logoutResponse(sessionHelper.getPerson(), samlHelper.prettyPrint(logoutResponse), false, serviceProvider.getName(null));

        } catch (RequesterException | ResponderException e) {
            log.error("Error occurred, no destination to send error known", e);
        }

        // Get logoutRequest that started logout
        LogoutRequest logoutRequest = null;
        try {
            logoutRequest = sessionHelper.getLogoutRequest();
            Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();

            logout(httpServletResponse, logoutRequest, spSessions);
        } catch (ResponderException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = StringUtils.hasLength(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
            errorResponseService.sendError(httpServletResponse, destination, logoutRequest != null ? logoutRequest.getID() : null, StatusCode.RESPONDER, ex);
        } catch (RequesterException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = StringUtils.hasLength(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
            errorResponseService.sendError(httpServletResponse, destination, logoutRequest != null ? logoutRequest.getID() : null, StatusCode.REQUESTER, ex);
        }
    }

    private void sendLogoutResponse(HttpServletResponse httpServletResponse, LogoutRequest logoutRequest) throws ResponderException, RequesterException {
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
        MessageContext<SAMLObject> messageContext = logoutResponseService.createMessageContextWithLogoutResponse(logoutRequest, destination);

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

    private void sendLogoutRequest(HttpServletResponse httpServletResponse, LogoutRequest logoutRequest) throws ResponderException, RequesterException {
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
    
    private void logout(HttpServletResponse response, LogoutRequest logoutRequest, Map<String, Map<String, String>> spSessions) throws ResponderException, RequesterException {
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
