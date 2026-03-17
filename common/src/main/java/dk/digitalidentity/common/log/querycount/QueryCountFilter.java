package dk.digitalidentity.common.log.querycount;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QueryCountFilter extends OncePerRequestFilter {
	private long thresholdInfo;
	private long thresholdWarn;
	private long thresholdError;
	
	public QueryCountFilter(long thresholdInfo, long thresholdWarn, long thresholdError) {
		this.thresholdInfo = thresholdInfo;
		this.thresholdWarn = thresholdWarn;
		this.thresholdError = thresholdError;
	}

	@Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        QueryCountingDataSource.start();
        
        try {
            chain.doFilter(request, response);
        }
        finally {
            int count = QueryCountingDataSource.getCount();
            
            if (count > thresholdError) {
            	log.error("{} {} — SQL statements: {}", request.getMethod(), request.getRequestURI(), count);
            }
            else if (count > thresholdWarn) {
            	log.warn("{} {} — SQL statements: {}", request.getMethod(), request.getRequestURI(), count);
            }
            else if (count > thresholdInfo) {
            	log.info("{} {} — SQL statements: {}", request.getMethod(), request.getRequestURI(), count);
            }

            QueryCountingDataSource.clear();
        }
    }
}
