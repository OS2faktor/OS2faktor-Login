package dk.digitalidentity.config.thymeleaf;

import java.util.Set;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OS2faktorConfigurationExpressionObjectFactory implements IExpressionObjectFactory {
    private static final String OS2FAKTOR_CONFIGURATION = "os2faktorConfiguration";
    private static final Set<String> NAMES = Set.of(OS2FAKTOR_CONFIGURATION);

    private final OS2faktorConfiguration os2faktorConfiguration;

    @Override
    public Set<String> getAllExpressionObjectNames() {
        return NAMES;
    }

    @Override
    public Object buildObject(IExpressionContext context, String expressionObjectName) {
        if (OS2FAKTOR_CONFIGURATION.equals(expressionObjectName)) {
            return new OS2faktorConfigurationExpressionObject(os2faktorConfiguration);
        }

        return null;
    }

    @Override
    public boolean isCacheable(String expressionObjectName) {
        return true;
    }
}
