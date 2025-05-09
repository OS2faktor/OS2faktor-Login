package dk.digitalidentity.controller;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.opensaml.saml.common.SAMLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.PropertySource;
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
import dk.digitalidentity.common.service.TUTermsAndConditionsService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@PropertySource("classpath:git.properties")
public class DefaultController implements ErrorController {
	private ErrorAttributes errorAttributes = new DefaultErrorAttributes();

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private TermsAndConditionsService termsAndConditionsService;

	@Autowired
	private TUTermsAndConditionsService tuTermsAndConditionsService;

	@Autowired
	private PrivacyPolicyService privacyPolicyService;
	
	@Autowired
	private OS2faktorConfiguration config;
	
	@Value(value = "${git.commit.id.abbrev:}")
	private String gitCommitId;

	@Value(value = "${git.build.time:}")
	private String gitBuildTime;
	
	@GetMapping("/")
	public String defaultPage(Model model, HttpServletRequest request) {
		if (securityUtil.isAuthenticated()) {
			if (securityUtil.hasAnyAdminRole()) {
				return "redirect:/admin";
			}
			else if (securityUtil.isAuthenticated()) {
				return "redirect:/selvbetjening";
			}
		}
		
		if (config.isLandingPageEnabled()) {
			return "landingpage/index";
		}

		model.addAttribute("httpServletRequestRequestUrl", request.getRequestURL().toString());

		return "index";
	}
	
	@GetMapping("/info")
	public String infoPage(Model model) {
		model.addAttribute("gitBuildTime", gitBuildTime.substring(0, 10));
		model.addAttribute("gitCommitId", gitCommitId);
		model.addAttribute("releaseVersion", config.getVersion());

		return "info";
	}
	
	@GetMapping("/privatlivspolitik")
	public String privacyPage(Model model) {
		String tts = privacyPolicyService.getPrivacyPolicy().getLastUpdatedTts() != null ? privacyPolicyService.getPrivacyPolicy().getLastUpdatedTts().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Aldrig";
		
		model.addAttribute("privacy", privacyPolicyService.getPrivacyPolicy().getContent());
		model.addAttribute("tts", "Sidst redigeret: " + tts);

		return "privacy";
	}

	@GetMapping("/vilkaar")
	public String termAndConditionsPage(Model model) {
		String tts = termsAndConditionsService.getTermsAndConditions().getLastUpdatedTts() != null ? termsAndConditionsService.getTermsAndConditions().getLastUpdatedTts().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Aldrig";
		
		model.addAttribute("terms", termsAndConditionsService.getTermsAndConditions());
		model.addAttribute("tts", "Sidst redigeret: " + tts);

		return "terms-and-conditions";
	}
	
	@GetMapping("/tuvilkaar")
	public String tuTermAndConditionsPage(Model model) {
		String tts = tuTermsAndConditionsService.getTermsAndConditions().getLastUpdatedTts() != null ? termsAndConditionsService.getTermsAndConditions().getLastUpdatedTts().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Aldrig";
		
		model.addAttribute("terms", tuTermsAndConditionsService.getTermsAndConditions().getContent());
		model.addAttribute("tts", "Sidst redigeret: " + tts);

		return "tu-terms-and-conditions";
	}

	@GetMapping(value = { "/version" })
	public String newVersion(Model model) {
		return "version";
	}

	@RequestMapping(value = "/error", produces = "text/html")
	public String errorPage(Model model, HttpServletRequest request) {
		Map<String, Object> body = getErrorAttributes(new ServletWebRequest(request));

		// deal with SAML errors first
		Object status = body.get("status");
		if (status != null && status instanceof Integer) {
			if ((Integer) status == 999) {
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
			} else if ((Integer) status == 403 || (Integer) status == 401) {
				return "unauthorized";
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
