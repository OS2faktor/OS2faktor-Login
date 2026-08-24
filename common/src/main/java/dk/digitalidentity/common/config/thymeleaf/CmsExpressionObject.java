package dk.digitalidentity.common.config.thymeleaf;

import dk.digitalidentity.common.service.CmsMessageBundle;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CmsExpressionObject {
    private final CmsMessageBundle bundle;

    public String text(String key) {
        return bundle.getText(key);
    }

    public String text(String key, boolean bypassCache) {
    	return bundle.getText(key, bypassCache);
    }

    public boolean has(String key) {
        String v = bundle.getText(key);

        return v != null && !v.isEmpty();
    }
}
