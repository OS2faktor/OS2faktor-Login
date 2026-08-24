package dk.digitalidentity.config.thymeleaf;

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import dk.digitalidentity.config.OS2faktorConfiguration;

public class OS2faktorConfigurationDialect extends AbstractDialect implements IExpressionObjectDialect {
    private final IExpressionObjectFactory factory;

    public OS2faktorConfigurationDialect(OS2faktorConfiguration config) {
        super("os2faktorConfiguration");
        this.factory = new OS2faktorConfigurationExpressionObjectFactory(config);
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }
}