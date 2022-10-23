package dk.digitalidentity.controller;

import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.samlmodule.util.exceptions.NSISLevelTooLowException;

@Controller
public class DefaultController implements ErrorController {
	private ErrorAttributes errorAttributes = new DefaultErrorAttributes();

	@RequestMapping(value = "/error", produces = "text/html")
	public String errorPage(Model model, HttpServletRequest request, RedirectAttributes redirectAttributes) {
		Map<String, Object> body = getErrorAttributes(new ServletWebRequest(request));
		try {
			Object status = body.get("status");
			if (status != null && status instanceof Integer) {
				if ((Integer) status == 403 && Objects.equals((String) body.get("path"), "/sso/saml/changepassword")) {
					redirectAttributes.addFlashAttribute("errorMessage", "Din session er udløbet - du skal logge på forfra");
					return "redirect:/sso/saml/changepassword";
				}

				if ((Integer) status == 999) {
					Object authException = request.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
	
					// handle the forward case
					if (authException == null && request.getSession() != null) {
						authException = request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
					}
	
					if (authException != null && authException instanceof NSISLevelTooLowException) {
						return "error-nsis-level";
					}
				}
			}
		}
		catch (Exception ex) {
			; // ignore
		}

		// Default error message, IdP should not have any saml errors since these are sent back
		model.addAllAttributes(body);

		return "error";
	}

	@RequestMapping(value = "/error", produces = "application/json")
	public ResponseEntity<Map<String, Object>> errorJSON(HttpServletRequest request) {
		Map<String, Object> body = getErrorAttributes(new ServletWebRequest(request));

		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		try {
			status = HttpStatus.valueOf((int) body.get("status"));
		}
		catch (Exception ex) {
			;
		}

		return new ResponseEntity<>(body, status);
	}

	private Map<String, Object> getErrorAttributes(WebRequest request) {
		return errorAttributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());
	}
}
