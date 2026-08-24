package dk.digitalidentity.common.config.thymeleaf;

import dk.digitalidentity.common.config.CommonConfiguration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CommonConfigurationExpressionObject {
    private final CommonConfiguration common;

    public CommonConfiguration get() {
        return common;
    }
}
