package dk.digitalidentity.controller.wsfederation;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.controller.wsfederation.dto.WSFedRequestDTO;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.WSFederationService;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
public class WSFederationController {

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private LoginService loginService;

	@Autowired
	private WSFederationService wsFederationService;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@RequestMapping(value = "/ws/login", method = { RequestMethod.GET, RequestMethod.POST})
	public ModelAndView login(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model, @Valid WSFedRequestDTO parameters) throws RequesterException, ResponderException {
		// handle logout case
		if ("wsignout1.0".equals(parameters.getWa()) || "wsignoutcleanup1.0".equals(parameters.getWa())) {
			return logout(httpServletRequest, httpServletResponse, model, parameters);
		}

		sessionHelper.prepareNewLogin();

		// validate login request
		wsFederationService.validateLoginParameters(parameters);

		String destination;
		if (StringUtils.hasLength(parameters.getWreply())) {
			destination = parameters.getWreply();
		}
		else {
			ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(parameters.getWtrealm());
			Set<String> approvedEndpoints = wsFederationService.getApprovedEndpoints(serviceProvider);
			destination = approvedEndpoints.stream().findFirst().orElseThrow(() -> new ResponderException("No endpoint found"));
		}

		LoginRequest loginRequest = new LoginRequest(parameters, httpServletRequest.getHeader("User-Agent"), destination);
		loginRequest.setRelayState(parameters.getWctx());

		return loginService.loginRequestReceived(httpServletRequest, httpServletResponse, model, loginRequest);
	}

	private ModelAndView logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model, @Valid WSFedRequestDTO logoutParameters) throws RequesterException, ResponderException {

		// delete session and save logoutRequest
		Person person = sessionHelper.getPerson();
		if (person == null) {
			sessionHelper.invalidateSession();
		}
		else {
			auditLogger.logout(person);
			sessionHelper.logout(null);
		}

		if (StringUtils.hasLength(logoutParameters.getWreply())) {
			return new ModelAndView("redirect:" + logoutParameters.getWreply());
		}
		else if (StringUtils.hasLength(logoutParameters.getReply())) {
			return new ModelAndView("redirect:" + logoutParameters.getReply());
		}

        return new ModelAndView("redirect:/");
	}
}
