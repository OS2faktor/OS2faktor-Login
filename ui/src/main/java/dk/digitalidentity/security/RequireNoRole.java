package dk.digitalidentity.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RequireRoleAnnotation
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireNoRole {

}
