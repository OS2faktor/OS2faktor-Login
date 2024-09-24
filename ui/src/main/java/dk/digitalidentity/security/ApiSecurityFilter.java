package dk.digitalidentity.security;

import java.io.IOException;
import java.util.Objects;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Client;
import dk.digitalidentity.common.dao.model.enums.ApiRole;
import dk.digitalidentity.common.service.ClientService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiSecurityFilter implements Filter {

    @Setter
    private ApiRole apiRole;

    @Setter
    private ClientService clientService;

    @Setter
    private boolean enabled;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String authHeader = request.getHeader("ApiKey");

        if (!enabled) {
            unauthorized(response, "API (" + apiRole.toString() + ") is not enabled", authHeader);
            return;
        }

        if (StringUtils.hasLength(authHeader)) {
            boolean apiKeyMatch = checkApiKey(authHeader);

            if (!apiKeyMatch) {
                unauthorized(response, "Invalid ApiKey for " + apiRole.toString(), authHeader);
                return;
            }

            filterChain.doFilter(servletRequest, servletResponse);
        }
        else {
            unauthorized(response, "Missing ApiKey for " + apiRole.toString(), null);
        }
    }

    private void unauthorized(HttpServletResponse response, String message, String authHeader) throws IOException {
        log.warn(message + " (authHeader = " + authHeader + ")");

        response.sendError(401, message);
    }

    private boolean checkApiKey(String apiKey) {
        Client entry = clientService.findByApiKey(apiKey);
        
        if (entry != null) {
			if (Objects.equals(entry.getRole(), apiRole)) {
				return true;
			}
        }

        return false;
    }
}
