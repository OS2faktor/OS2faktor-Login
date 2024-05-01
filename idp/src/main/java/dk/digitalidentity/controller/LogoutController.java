package dk.digitalidentity.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
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
import dk.digitalidentity.service.LogoutService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

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
    private LogoutService logoutService;

    // user initiated logout from IdP UI
    @GetMapping("/sso/saml/logoutIdP")
    public String logoutIdp(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();

        try {
            logoutService.logout(httpServletResponse, null, spSessions);
        	return null;
        }
        catch (Exception ex) {
        	log.warn("Failed to perform SLO", ex);
        }

    	return "redirect:/";
    }

    @RequestMapping(value = "/sso/saml/logout", method = { POST, GET })
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
			log.warn("Error occurred, no destination to send error known", e);
			return null;
		}

        try {
            // Validate logout request
            EntityDescriptor spMetadata = serviceProvider.getMetadata();
            logoutRequestService.validateLogoutRequest(httpServletRequest, messageContext, spMetadata, serviceProvider);

            // Remove the EntityId of the SP sending the LogoutRequest since the user is no longer logged in there
            Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
            spSessions.remove(serviceProvider.getEntityId());
            sessionHelper.setServiceProviderSessions(spSessions);

            // TODO: virker forkert... bør logout requestet ikke komme fra IdP'en når den nu sendes til de andre SP'ere?
            // bør testes med 2 SP'ere for at se hvordan logout requestet ser ud når det sendes videre til næste IdP
            logoutService.logout(httpServletResponse, logoutRequest, spSessions);
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
        }
        catch (RequesterException | ResponderException e) {
            log.warn("Error occurred during logout response parsing", e);
        }

        // Get logoutRequest that started logout
        LogoutRequest logoutRequest = null;
        try {
            logoutRequest = sessionHelper.getLogoutRequest();
            Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();

            logoutService.logout(httpServletResponse, logoutRequest, spSessions);
        }
        catch (ResponderException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = StringUtils.hasLength(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
            errorResponseService.sendError(httpServletResponse, destination, logoutRequest != null ? logoutRequest.getID() : null, StatusCode.RESPONDER, ex);
        }
        catch (RequesterException ex) {
            SingleLogoutService endpoint = serviceProvider.getLogoutResponseEndpoint();
            String destination = StringUtils.hasLength(endpoint.getResponseLocation()) ? endpoint.getResponseLocation() : endpoint.getLocation();
            errorResponseService.sendError(httpServletResponse, destination, logoutRequest != null ? logoutRequest.getID() : null, StatusCode.REQUESTER, ex);
        }
    }
}
