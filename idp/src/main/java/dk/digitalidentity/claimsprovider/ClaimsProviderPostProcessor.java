package dk.digitalidentity.claimsprovider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.samlmodule.model.SamlLoginPostProcessor;
import dk.digitalidentity.samlmodule.model.TokenUser;

@Component
public class ClaimsProviderPostProcessor implements SamlLoginPostProcessor {

	@Autowired
	private ClaimsProviderUtil claimsProviderUtil;

	@Override
	public void process(TokenUser tokenUser) {
		claimsProviderUtil.updateTokenUser(tokenUser);
	}
}
