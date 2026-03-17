package dk.digitalidentity.common.log.querycount;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "query-counter.enabled", havingValue = "true")
public class QueryCountDataSourceConfig {

	@Value(value = "${query-counter.threshold.info:0}")
	private long thresholdInfo;
	
	@Value(value = "${query-counter.threshold.warn:19}")
	private long thresholdWarn;
	
	@Value(value = "${query-counter.threshold.error:99}")
	private long thresholdError;

    @Bean
    public BeanPostProcessor queryCountingDataSourcePostProcessor() {
        return new BeanPostProcessor() {
        	
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource ds && !(bean instanceof QueryCountingDataSource)) {
                    return new QueryCountingDataSource(ds);
                }

                return bean;
            }
        };
    }
    
    @Bean
    public QueryCountFilter queryCountFilter() {
        return new QueryCountFilter(thresholdInfo, thresholdWarn, thresholdError);
    }
}
