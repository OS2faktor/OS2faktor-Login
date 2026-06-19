package dk.digitalidentity.common.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.FormContentFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SafeFormContentFilter extends FormContentFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            super.doFilterInternal(request, response, filterChain);
        }
        catch (Exception ex) {
            if (ex.getCause() instanceof IllegalArgumentException) {
            	log.warn("Rejected malformed form payload [method={}, uri={}, contentType={}, from={}]: {}",
            	        request.getMethod(),
            	        request.getRequestURI(),
            	        request.getContentType(),
            	        request.getRemoteAddr(),
            	        ex.getMessage());

                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
            else {
                throw ex;
            }
        }
    }
}
