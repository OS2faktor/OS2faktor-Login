package dk.digitalidentity.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_ADMINISTRATOR') or hasRole('ROLE_KODEVISER_ADMIN')")
public @interface RequireKodeviserAdministrator {

}
