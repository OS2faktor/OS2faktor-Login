package dk.digitalidentity.service;

import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ErrorHandlingService {
	public static final String ERROR_PAGE = "error-with-help";
	public static final String NULL_STR = "<null>";

	@Autowired
	private SessionHelper sessionHelper;

	public String error(String location, Model model) {
		logError(location, null, null);
		model.addAttribute("location", location);
		model.addAttribute("timestamp", new LocalDateTime().toString());
		return ERROR_PAGE;
	}

	public String error(String location, HttpServletRequest request, String message , Model model) {
		logError(location, request, message);
		model.addAttribute("location", location);
		model.addAttribute("timestamp", new LocalDateTime().toString());
		return ERROR_PAGE;
	}

	public ModelAndView modelAndViewError(String location, Model model) {
		return modelAndViewError(location, null, null, model);
	}

	public ModelAndView modelAndViewError(String location, HttpServletRequest request, String message, Model model) {
		return modelAndViewError(location, request, message, model, false);
	}

	public ModelAndView modelAndViewError(String location, HttpServletRequest request, String message, Model model, boolean technicalError) {
		logError(location, request, message);
		model.addAttribute("location", location);
		model.addAttribute("timestamp", new LocalDateTime().toString());
		model.addAttribute("technicalError", technicalError);
		return new ModelAndView(ERROR_PAGE, model.asMap());
	}

	private void logError(String location, HttpServletRequest request, String message) {
		try {
			if (!StringUtils.hasLength(location)) {
				location = NULL_STR;
			}
	
			if (!StringUtils.hasLength(message)) {
				message = NULL_STR;
			}
	
			String personId = NULL_STR;
			if (sessionHelper.getPerson() != null) {
				personId = Long.toString(sessionHelper.getPerson().getId());
			}
	
			String referer = NULL_STR;
			if (request != null) {
				String refererHeader = request.getHeader("referer");
				if (StringUtils.hasLength(refererHeader)) {
					referer = refererHeader;
				}
			}

			StringBuilder sb = new StringBuilder();
			sb.append("Person: '").append(personId).append("'\n");
			sb.append("Location: '").append(location).append("'\n");
			sb.append("Referer: '").append(referer).append("'\n");
			sb.append("Message: '").append(message).append("'\n");
			sb.append("IP: '").append(getIpAddress(request)).append("'\n");
			sb.append("Session: \n").append(sessionHelper.serializeSessionAsString());
			
			log.warn(sb.toString());
		}
		catch (Exception ex) {
			log.error("Failed to log a warning for location = " + location + ", message = " + message, ex);
		}
	}
	
	private static String getIpAddress(HttpServletRequest request) {
		String remoteAddr = "";

		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}
}
