package dk.digitalidentity.claimsprovider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.samlmodule.controller.DISAML_LoginController;
import dk.digitalidentity.samlmodule.util.exceptions.ExternalException;
import dk.digitalidentity.samlmodule.util.exceptions.PassiveLoginFailedException;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.IdPFlowException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice(assignableTypes = { DISAML_LoginController.class, ClaimsProviderController.class })
public class NonNsisProviderControllerAdvice {

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private LoginService loginService;
	
	@Autowired
	private AuditLogger auditLogger;

	@ExceptionHandler(exception = { PassiveLoginFailedException.class, ExternalException.class })
	public ModelAndView handleLoginErrors(final Exception ex, final Model model, final HttpServletRequest request, final HttpServletResponse response) throws IdPFlowException {
		log.warn("Login with non-nsis IdP failed : " + ex.getMessage());
		
		auditLogger.externalIdPFailed(ex.getMessage());

		model.addAttribute("flashMessage", "Loginmetode fejlede - anvend normal login i stedet");

		return loginService.loginRequestReceived(request, response, model, sessionHelper.getLoginRequest(), true);
	}
}
