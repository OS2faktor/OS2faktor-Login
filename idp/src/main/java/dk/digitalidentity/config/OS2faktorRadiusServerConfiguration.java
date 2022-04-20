package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.RadiusClientService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.radius.OS2faktorRadiusServer;
import dk.digitalidentity.service.LoginService;

@Configuration
public class OS2faktorRadiusServerConfiguration {

	@Autowired
	private MFAService mfaService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private LoginService loginService;
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private RadiusClientService radiusClientService;
	
	@Autowired
	private AuditLogger auditLogger;

	@Bean(name = "radiusServerWithoutMfa")
	public OS2faktorRadiusServer radiusServerWithoutMfa() {
		return new OS2faktorRadiusServer(1812, false, personService, mfaService, commonConfiguration.getRadiusConfiguration(), loginService, radiusClientService, auditLogger);
	}

	@Bean(name = "radiusServerWithMfa")
	public OS2faktorRadiusServer radiusServerWithMfa() {
		return new OS2faktorRadiusServer(1813, true, personService, mfaService, commonConfiguration.getRadiusConfiguration(), loginService, radiusClientService, auditLogger);
	}
}
