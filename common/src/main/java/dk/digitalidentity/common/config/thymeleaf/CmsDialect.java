package dk.digitalidentity.common.config.thymeleaf;

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import dk.digitalidentity.common.service.CmsMessageBundle;

public class CmsDialect extends AbstractDialect implements IExpressionObjectDialect {
    private final IExpressionObjectFactory factory;

    public CmsDialect(CmsMessageBundle bundle) {
        super("cms");
        this.factory = new CmsExpressionObjectFactory(bundle);
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }
}