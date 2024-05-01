package dk.digitalidentity.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import dk.digitalidentity.common.service.CmsMessageBundle;
import dk.digitalidentity.util.IdPFlowException;
import dk.digitalidentity.util.ResponderException;

@ControllerAdvice
public class IdPFlowControllerAdvice {

	@Autowired
	private CmsMessageBundle bundle;
	
	@ExceptionHandler(IdPFlowException.class )
	public String handleException(Model model, IdPFlowException ex) {
		model.addAttribute("origin", (ex instanceof ResponderException) ? "responder" : "requester");
		model.addAttribute("errorMessage", ex.getMessage());
		model.addAttribute("errorCode", ex.getErrorCode());
		model.addAttribute("helpMessage", bundle.getText(ex.getHelpMessage()));

		return "error-idp";
	}
}
