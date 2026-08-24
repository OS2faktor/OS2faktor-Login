package dk.digitalidentity.common.config.thymeleaf;

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import dk.digitalidentity.common.config.CommonConfiguration;

public class CommonConfigurationDialect extends AbstractDialect implements IExpressionObjectDialect {
    private final IExpressionObjectFactory factory;

    public CommonConfigurationDialect(CommonConfiguration config) {
        super("commonConfiguration");
        this.factory = new CommonConfigurationExpressionObjectFactory(config);
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }
}