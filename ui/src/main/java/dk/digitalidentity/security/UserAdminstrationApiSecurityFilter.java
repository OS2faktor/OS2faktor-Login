package dk.digitalidentity.security;

import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

@Slf4j
public class UserAdminstrationApiSecurityFilter implements Filter {
	private OS2faktorConfiguration configuration;

	public void setConfiguration(OS2faktorConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		Filter.super.init(filterConfig);
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		String authHeader = request.getHeader("ApiKey");

		if (configuration.getUserAdminstration().isEnabled()) {
			if (authHeader != null) {
				boolean apiKeyMatch = false;

				if (!StringUtils.hasLength(configuration.getUserAdminstration().getApiKey())) {
					apiKeyMatch = false;
				}
				else if (Objects.equals(configuration.getUserAdminstration().getApiKey(), authHeader)) {
					apiKeyMatch = true;
				}

				if (!apiKeyMatch) {
					unauthorized(response, "Invalid ApiKey header (userAdminstration)", authHeader);
					return;
				}

				filterChain.doFilter(servletRequest, servletResponse);
			}
			else {
				unauthorized(response, "Missing ApiKey header (userAdminstration)", authHeader);
			}
		}
		else {
			unauthorized(response, "User adminstration via API is not enabled", authHeader);
		}
	}

	private static void unauthorized(HttpServletResponse response, String message, String authHeader) throws IOException {
		log.warn(message + " (authHeader = " + authHeader + ")");
		response.sendError(401, message);
	}

	@Override
	public void destroy() {
		Filter.super.destroy();
	}
}
