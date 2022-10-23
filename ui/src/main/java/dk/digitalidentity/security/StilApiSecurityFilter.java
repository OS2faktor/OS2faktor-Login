package dk.digitalidentity.security;

import java.io.IOException;
import java.util.Objects;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StilApiSecurityFilter implements Filter {
	private CommonConfiguration configuration;

	public void setConfiguration(CommonConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		// we are using a custom header instead of Authorization because the Authorization header plays very badly with the SAML filter
		String authHeader = request.getHeader("ApiKey");
		if (authHeader != null) {
			boolean apiKeyMatch = false;

			if (!StringUtils.hasLength(configuration.getStilStudent().getApiKey())) {
				apiKeyMatch = false;
			}
			else if (Objects.equals(configuration.getStilStudent().getApiKey(), authHeader)) {
				apiKeyMatch = true;
			}
			
			if (!apiKeyMatch) {
				unauthorized(response, "Invalid ApiKey header (Stil)", authHeader);
				return;
			}

			filterChain.doFilter(servletRequest, servletResponse);
		}
		else {
			unauthorized(response, "Missing ApiKey header (Stil)", authHeader);
		}
	}

	private static void unauthorized(HttpServletResponse response, String message, String authHeader) throws IOException {
		log.warn(message + " (authHeader = " + authHeader + ")");
		response.sendError(401, message);
	}

	@Override
	public void destroy() {
		;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		;
	}
}
