package dk.digitalidentity.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_SUPPORTER') or hasRole('ROLE_REGISTRANT') or hasRole('ROLE_SERVICE_PROVIDER_ADMIN') or hasRole('ROLE_USER_ADMIN') or hasRole('ROLE_KODEVISER_ADMIN') or hasRole('ROLE_PASSWORD_RESET_ADMIN')")
public @interface RequireAnyAdminRole {

}
