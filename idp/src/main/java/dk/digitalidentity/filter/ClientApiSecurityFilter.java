package dk.digitalidentity.filter;

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

import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import dk.digitalidentity.common.service.WindowCredentialProviderClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class ClientApiSecurityFilter implements Filter {
    private WindowCredentialProviderClientService clientService;

    public void setWindowsClientService(WindowCredentialProviderClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String clientID = request.getHeader("clientID");
        if (clientID == null) {
            unauthorized(response, "Missing clientID header", clientID);
            return;
        }

        WindowCredentialProviderClient client = clientService.getByNameAndDisabledFalse(clientID);
        if (client == null) {
            unauthorized(response, "Invalid ClientID header", clientID);
            return;
        }

        String apiKey = request.getHeader("apiKey");
        if (apiKey == null) {
            unauthorized(response, "Missing apiKey header", apiKey);
            return;
        }

        if (!StringUtils.hasLength(apiKey) || !Objects.equals(client.getApiKey(), apiKey)) {
            unauthorized(response, "Invalid ApiKey", apiKey);
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private static void unauthorized(HttpServletResponse response, String message, String value) throws IOException {
        log.warn(message + " (value = " + value + ")");
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
