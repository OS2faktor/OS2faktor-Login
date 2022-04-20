package dk.digitalidentity.nemlogin;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.samlmodule.model.SamlLoginPostProcessor;
import dk.digitalidentity.samlmodule.model.TokenUser;

@Component
@Transactional
public class NemLoginPostProcessor implements SamlLoginPostProcessor {

	@Autowired
	private NemLoginUtil nemLoginUtil;

	@Override
	public void process(TokenUser tokenUser) {
		// Send token to the util class
		nemLoginUtil.updateTokenUser(tokenUser);
	}
}
