package dk.digitalidentity.common.config.thymeleaf;

import java.util.Set;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

import dk.digitalidentity.common.config.CommonConfiguration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CommonConfigurationExpressionObjectFactory implements IExpressionObjectFactory {
    private static final String COMMON_CONFIGURATION = "commonConfiguration";
    private static final Set<String> NAMES = Set.of(COMMON_CONFIGURATION);

    private final CommonConfiguration commonConfiguration;

    @Override
    public Set<String> getAllExpressionObjectNames() {
        return NAMES;
    }

    @Override
    public Object buildObject(IExpressionContext context, String expressionObjectName) {
        if (COMMON_CONFIGURATION.equals(expressionObjectName)) {
            return new CommonConfigurationExpressionObject(commonConfiguration);
        }

        return null;
    }

    @Override
    public boolean isCacheable(String expressionObjectName) {
        return true;
    }
}
