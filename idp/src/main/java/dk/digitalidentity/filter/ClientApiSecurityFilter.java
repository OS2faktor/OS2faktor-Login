package dk.digitalidentity.filter;

import java.io.IOException;

import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import dk.digitalidentity.common.service.WindowCredentialProviderClientService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientApiSecurityFilter implements Filter {
	public static ThreadLocal<Domain> domainHolder = new ThreadLocal<>();
    private WindowCredentialProviderClientService clientService;

    public void setWindowsClientService(WindowCredentialProviderClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String apiKey = request.getHeader("apiKey");
        if (!StringUtils.hasLength(apiKey)) {
            unauthorized(request, response, "Missing apiKey header");
            return;
        }

        WindowCredentialProviderClient client = clientService.getByApiKeyAndDisabledFalse(apiKey);
        if (client == null) {
            unauthorized(request, response, "Invalid apiKey header: " + apiKey);
            return;
        }

        try {
        	domainHolder.set(client.getDomain());
        	filterChain.doFilter(servletRequest, servletResponse);
        }
        finally {
        	domainHolder.remove();
        }
    }

    private static void unauthorized(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
    	log.warn(message + ", path = " + request.getServletPath());
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
