package dk.digitalidentity.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.opensaml.saml.common.SAMLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import dk.digitalidentity.common.service.PrivacyPolicyService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.security.SecurityUtil;

@Controller
public class DefaultController implements ErrorController {
	private ErrorAttributes errorAttributes = new DefaultErrorAttributes();

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private TermsAndConditionsService termsAndConditionsService;

	@Autowired
	private PrivacyPolicyService privacyPolicyService;
	
	@GetMapping("/")
	public String defaultPage() {
		if (securityUtil.isAuthenticated()) {
			if (securityUtil.hasAnyAdminRole()) {
				return "redirect:/admin";
			}
			else if (securityUtil.isAuthenticated()) {
				return "redirect:/selvbetjening";
			}
		}

		return "index";
	}
	
	@GetMapping("/privatlivspolitik")
	public String privacyPage(Model model) {
		model.addAttribute("privacy", privacyPolicyService.getPrivacyPolicy().getContent());
		return "privacy";
	}

	@GetMapping("/vilkaar")
	public String termAndConditionsPage(Model model) {
		model.addAttribute("terms", termsAndConditionsService.getTermsAndConditions().getContent());
		return "terms-and-conditions";
	}

	@RequestMapping(value = "/error", produces = "text/html")
	public String errorPage(Model model, HttpServletRequest request) {
		Map<String, Object> body = getErrorAttributes(new ServletWebRequest(request));

		// deal with SAML errors first
		Object status = body.get("status");
		if (status != null && status instanceof Integer && (Integer) status == 999) {
			Object authException = request.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);

			// handle the forward case
			if (authException == null && request.getSession() != null) {
				authException = request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
			}

			if (authException != null && authException instanceof Throwable) {
				StringBuilder builder = new StringBuilder();
				Throwable t = (Throwable) authException;

				logThrowable(builder, t, false);
				model.addAttribute("exception", builder.toString());

				if (t.getCause() != null) {
					t = t.getCause();

					// deal with the known causes for this error
					if (t instanceof SAMLException) {
						String message = t.getMessage();

						String[] split = message.split(", status message is ");
						String actualMessage = split[split.length - 1];
						if (StringUtils.hasLength(actualMessage)) {
							model.addAttribute("message", actualMessage);
						}
					}
					else {
						model.addAttribute("cause", "UNKNOWN");
					}
				}

				return "samlerror";
			}
		}

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

	private void logThrowable(StringBuilder builder, Throwable t, boolean append) {
		StackTraceElement[] stackTraceElements = t.getStackTrace();

		builder.append((append ? "Caused by: " : "") + t.getClass().getName() + ": " + t.getMessage() + "\n");
		for (int i = 0; i < 5 && i < stackTraceElements.length; i++) {
			builder.append("  ... " + stackTraceElements[i].toString() + "\n");
		}

		if (t.getCause() != null) {
			logThrowable(builder, t.getCause(), true);
		}
	}
}
