package dk.digitalidentity.common.config.thymeleaf;

import java.util.Set;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

import dk.digitalidentity.common.service.CmsMessageBundle;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CmsExpressionObjectFactory implements IExpressionObjectFactory {
    private static final String CMS = "cms";
    private static final Set<String> NAMES = Set.of(CMS);

    private final CmsMessageBundle bundle;

    @Override
    public Set<String> getAllExpressionObjectNames() {
        return NAMES;
    }

    @Override
    public Object buildObject(IExpressionContext context, String expressionObjectName) {
        if (CMS.equals(expressionObjectName)) {
            return new CmsExpressionObject(bundle);
        }

        return null;
    }

    @Override
    public boolean isCacheable(String expressionObjectName) {
        return true;
    }
}
