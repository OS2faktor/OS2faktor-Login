package dk.digitalidentity.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

// TODO: dead role, right?
@RequireRoleAnnotation
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_REGISTRANT')")
public @interface RequireRegistrant {

}
