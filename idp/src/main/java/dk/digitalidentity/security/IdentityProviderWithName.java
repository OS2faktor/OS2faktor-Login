package dk.digitalidentity.security;

import dk.digitalidentity.samlmodule.model.IdentityProvider;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdentityProviderWithName extends IdentityProvider {
	private String name;
}
