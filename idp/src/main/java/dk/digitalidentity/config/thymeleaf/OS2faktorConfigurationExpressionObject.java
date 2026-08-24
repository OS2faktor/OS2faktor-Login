package dk.digitalidentity.config.thymeleaf;

import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OS2faktorConfigurationExpressionObject {
    private final OS2faktorConfiguration configuration;

    public OS2faktorConfiguration get() {
        return configuration;
    }
}
