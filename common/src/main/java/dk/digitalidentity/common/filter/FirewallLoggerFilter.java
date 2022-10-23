package dk.digitalidentity.common.filter;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FirewallLoggerFilter implements RequestRejectedHandler {

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, RequestRejectedException requestRejectedException) throws IOException, ServletException {
        log.warn("request_rejected: remote={}, user_agent={}, request_url={}, request_method={}, message={}",
        	getIpAddress(request),
            request.getHeader(HttpHeaders.USER_AGENT),
            request.getRequestURL(),
            request.getMethod(),
            ((requestRejectedException != null) ? requestRejectedException.getMessage() : "<null>")
        );

        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
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
