package dk.digitalidentity.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.common.dao.model.PersonAttribute;
import dk.digitalidentity.common.service.PersonAttributeService;
import org.joda.time.DateTime;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.LoginInfoMessageService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.PrivacyPolicyService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.dto.ClaimValueDTO;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.service.serviceprovider.SqlServiceProvider;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;

@Service
@Slf4j
public class LoginService {

    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private ServiceProviderFactory serviceProviderFactory;

    @Autowired
    private ADPasswordService adPasswordService;

    @Autowired
    private NemIDService nemIDService;

    @Autowired
    private MFAService mfaService;

    @Autowired
    private LoginInfoMessageService loginInfoMessageService;

    @Autowired
    private PersonService personService;

    @Autowired
    private AssertionService assertionService;

    @Autowired
    private LoggingUtil loggingUtil;

    @Autowired
    private AuthnRequestHelper authnRequestHelper;

    @Autowired
    private ErrorResponseService errorResponseService;

    @Autowired
    private AuthnRequestService authnRequestService;

    @Autowired
    private PasswordSettingService passwordSettingService;

    @Autowired
    private TermsAndConditionsService termsAndConditionsService;

    @Autowired
    private PrivacyPolicyService privacyPolicyService;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private OS2faktorConfiguration configuration;

    @Autowired
    private CommonConfiguration commonConfiguration;

    @Autowired
    private PersonAttributeService personAttributeService;

    public ModelAndView initiateFlowOrCreateAssertion(Model model, HttpServletResponse response, HttpServletRequest request, Person person) throws ResponderException, RequesterException {
        ResponderException cannotPerformPassiveLogin = new ResponderException("Passiv login krævet, men bruger er ikke logget ind på det krævede niveau");

        AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
        if (authnRequest == null) {
        	String redirectUrl = sessionHelper.getPasswordChangeSuccessRedirect();
        	sessionHelper.clearFlowStates();

        	// we may end up here in special cases after finishing certain change-password subflows, and if we have a redirect URL,
        	// we should attempt to use that to finish the flow, otherwise send them to the front-page of the IdP
        	if (redirectUrl != null) {
    			redirectUrl = sessionHelper.getPasswordChangeSuccessRedirect();
    			model.addAttribute("redirectUrl", redirectUrl);

                return new ModelAndView("changePassword/change-password-success", model.asMap());
        	}
        	
        	// frontpage of IdP
        	return new ModelAndView("redirect:/");
        }
        
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
        NSISLevel currentNSISLevel = sessionHelper.getLoginState();
        NSISLevel requiredNSISLevel = serviceProvider.nsisLevelRequired(authnRequest);

        if (requiredNSISLevel == NSISLevel.HIGH) {
            throw new ResponderException("Understøtter ikke NSIS Høj");
        }

        boolean valid = sessionHelper.handleValidateIP();

        // if no login state or changed IP, initiate login
        if (!valid || currentNSISLevel == null) {
            if (authnRequest.isPassive()) {
                throw cannotPerformPassiveLogin;
            }

            // Shortcut to NemID login. no reason to go through password login below if NemID is going to be required later in the flow
            if (authnRequestService.requireNemId(authnRequest)) {
                return initiateNemIDOnlyLogin(model, request, null);
            }

            return initiateLogin(model, request, serviceProvider.preferNemId());
        }

        // At this point the user is actually logged in, so we can start validation against the session

        // Should be caught earlier, but this is an extra check so we never approve a user that is not allowed access to a specific service
        RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
        if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
            auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
            throw new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
        }

        // Has the user approved conditions?
        if (requireApproveConditions(person)) {
            if (authnRequest.isPassive()) {
                throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
            }
            else {
                return initiateApproveConditions(model);
            }
        }

        // Has the user activated their NSIS User?
        boolean declineUserActivation = sessionHelper.isDeclineUserActivation();
        if (!declineUserActivation && person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
            if (!authnRequest.isPassive()) {
                return initiateActivateNSISAccount(model);
            }
        }

        // if the services provider requires NSIS, perform the following controls
        if (NSISLevel.LOW.equalOrLesser(requiredNSISLevel)) {

            // is user allowed to login to service providers requiring NSIS
            if (!person.isNsisAllowed()) {
                throw new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
            }

            // does the user have the required NSIS level?
			ModelAndView selectClaimsPage = null;
	        switch (currentNSISLevel) {
	            case SUBSTANTIAL:
                    selectClaimsPage = initiateSelectClaims(person, model, serviceProvider);
                    if (selectClaimsPage != null) {
                        if (authnRequest.isPassive()) {
                            throw cannotPerformPassiveLogin;
                        }
                        return selectClaimsPage;
                    }

	                return createAndSendAssertion(response, person, serviceProvider);
	            case LOW:
	            	// if the service provider requires SUBSTANTIAL, then perform a step-up
	                if (NSISLevel.SUBSTANTIAL.equalOrLesser(requiredNSISLevel)) {
	                    if (authnRequest.isPassive()) {
	                        throw cannotPerformPassiveLogin;
	                    }

	                    if (authnRequestService.requireNemId(authnRequest)) {
	                        return initiateNemIDOnlyLogin(model, request, null);
	                    }

	                    if (!NSISLevel.SUBSTANTIAL.equalOrLesser(person.getNsisLevel())) {
	                        throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der kræver et NSIS sikringsniveau på Lav");
	                    }

                        return initiateMFA(model, person, NSISLevel.SUBSTANTIAL);
	                }

                    selectClaimsPage = initiateSelectClaims(person, model, serviceProvider);
                    if (selectClaimsPage != null) {
                        if (authnRequest.isPassive()) {
                            throw cannotPerformPassiveLogin;
                        }
                        return selectClaimsPage;
                    }

	                return createAndSendAssertion(response, person, serviceProvider);
	            case NONE:
                    if (authnRequest.isPassive()) {
                        throw cannotPerformPassiveLogin;
                    }

                    if (!NSISLevel.LOW.equalOrLesser(person.getNsisLevel())) {
                        throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der ikke kræver et NSIS sikringsniveau");
                    }

                    RequireNemIdReason reason = authnRequestService.requireNemId(authnRequest) ? null : RequireNemIdReason.AD;
                    return initiateNemIDOnlyLogin(model, request, reason);
	            default:
	            	// catch-all, should not really happen though, due to previous validations
	                sessionHelper.clearSession();
	                if (authnRequestService.requireNemId(authnRequest)) {
	                    return initiateNemIDOnlyLogin(model, request, null);
	                }

	                return initiateLogin(model, request, serviceProvider.preferNemId());
	        }
        }

        // in this case, the service provider does not require NSIS
        if (serviceProvider.mfaRequired(authnRequest) && !NSISLevel.NONE.equalOrLesser(sessionHelper.getMFALevel())) {
            if (authnRequest.isPassive()) {
                throw cannotPerformPassiveLogin;
            }

            if (!allowMfaBypass(serviceProvider, person)) {
                return initiateMFA(model, person, NSISLevel.NONE);
            }
        }

        // If a person was manually activated, after successful password login they must change their password
        ModelAndView forceChangePassword = initiateForceChangePassword(person, model);
        if (forceChangePassword != null) {
            sessionHelper.setInForceChangePasswordFlow(true);
            return forceChangePassword;
        }

        ModelAndView selectClaimsPage = initiateSelectClaims(person, model, serviceProvider);
        if (selectClaimsPage != null) {
            return selectClaimsPage;
        }

        // user is already logged in at the required level
        return createAndSendAssertion(response, person, serviceProvider);
    }

    private boolean allowMfaBypass(ServiceProvider serviceProvider, Person person) {
        boolean allowMfaBypass = false;

        boolean selfRegisterFeatureEnabled = configuration.isAllowUsernamePasswordLoginIfNoMfa();
        boolean isSelfService = serviceProvider instanceof SelfServiceServiceProvider;

        // if (and only if), the above feature is enabled, and this is SelfService the user is trying to login
        // to, and the user is a non-NSIS user, AND the user does not already have a MFA device, THEN we allow
        // them to login without MFA so they can self-register the first MFA device
        if (selfRegisterFeatureEnabled && isSelfService && NSISLevel.LOW.equalOrLesser(person.getNsisLevel())) {
        	List<MfaClient> mfaClients = mfaService.getClients(person.getCpr());

        	if (mfaClients == null || mfaClients.isEmpty()) {
        		allowMfaBypass = true;
        	}
        }

        return allowMfaBypass;
    }

    public PasswordValidationResult validatePassword(String password, Person person) {
        return validatePassword(password, person, true);
    }

    public PasswordValidationResult validatePassword(String password, Person person, boolean validateAgainstAd) {
        PasswordValidationResult result = null;

        if (!StringUtils.hasLength(password)) {
            sessionHelper.setPasswordLevel(null);
            result = PasswordValidationResult.INVALID;
        }
        else if (StringUtils.hasLength(person.getNsisPassword())) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());

            if (encoder.matches(password, person.getNsisPassword())) {
                // Password matches, check for expiry

                if (settings.isForceChangePasswordEnabled() && person.getNsisPasswordTimestamp() != null) {
                    LocalDateTime maxPasswordAge = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());

                    if (!person.getNsisPasswordTimestamp().isAfter(maxPasswordAge)) {
                        log.warn("Local password validation failed due to expired password for person " + person.getId());

                        result = PasswordValidationResult.VALID_EXPIRED;
                    }
                }

                // Password matches and not expired
                if (result == null) {
                    sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
                    sessionHelper.setAuthnInstant(new DateTime());
                    result = PasswordValidationResult.VALID;
                }
            }
        }

        // fallback to validating against AD
        if (result == null && validateAgainstAd && StringUtils.hasLength(person.getSamaccountName())) {
            if (adPasswordService.validatePassword(person, password)) {
                sessionHelper.setAuthenticatedWithADPassword(true);
                sessionHelper.setADPerson(person);
                sessionHelper.setPasswordLevel(NSISLevel.NONE);
                sessionHelper.setAuthnInstant(new DateTime());
                sessionHelper.setPassword(password);
                result = PasswordValidationResult.VALID;
            }
        }

        if (result == null) {
            sessionHelper.setPasswordLevel(null);
            result = PasswordValidationResult.INVALID;
        }

        if (PasswordValidationResult.INVALID.equals(result)) {
            personService.badPasswordAttempt(person);
        }

        return result;
    }

    public List<Person> getPeople(String username) {
        if (!StringUtils.hasLength(username)) {
            return null;
        }

        // Allow users to input username with domain, for now disregard domain
        if (username.contains("@")) {
            String[] split = username.split("@");
            username = split[0];
        }

        List<Person> people = new ArrayList<>();

        // Login with os2faktor userid
        Person person = personService.getByUserId(username);
        if (person != null) {
            people.add(person);
        }

        // Get all persons with SAMAccountName equals to provided username
        people.addAll(personService.getBySamaccountName(username));

        return people;
    }

    public ModelAndView initiateLogin(Model model, HttpServletRequest request, boolean preferNemid) {
        return initiateLogin(model, request, preferNemid, false, null);
    }

    public ModelAndView initiateLogin(Model model, HttpServletRequest request, boolean preferNemid, boolean incorrectInput, String username) {
        nemIDService.populateModel(model, request);
        model.addAttribute("infobox", loginInfoMessageService.getInfobox());

        String params = URLEncoder.encode("/sso/saml/login?" + request.getQueryString(), StandardCharsets.UTF_8);
        model.addAttribute("changePasswordUrl", "/sso/saml/forgotpworlocked?redirectUrl=" + params);

        if (incorrectInput) {
            model.addAttribute("incorrectInput", true);
            model.addAttribute("preferNemid", false);
            model.addAttribute("username", username);
        }
        else {
            model.addAttribute("preferNemid", preferNemid);
        }

        // if "Using NemLog-in for other SPs feature" is not enabled, we will only show NemLog-in on SelfService
        model.addAttribute("showNemLogIn", showNemLogIn());

        return new ModelAndView("login", model.asMap());
    }

    private boolean showNemLogIn() {
        if (commonConfiguration.getNemlogin().isBrokerEnabled()) {
            return true;
        }
        else {
            // Try to determine if ServiceProvider is the SelfService since it is always allowed to use NemLog-in
            try {
                AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
                if (authnRequest != null) {
                    ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
                    if (serviceProvider != null && serviceProvider instanceof SelfServiceServiceProvider) {
                        return true;
                    }
                }
            }
            catch (RequesterException | ResponderException ex) {
                log.warn("Could not determine where user is trying to login to", ex);
            }

            // If broker is not enabled, default to deny access if we can't determine ServiceProvider OR it is not SelfService
            return false;
        }
    }

    public ModelAndView initiateUserSelect(Model model, List<Person> people) {
    	// If the person is in a dedicated activation flow,
        // the list of available people will be filtered to only match the people that CAN be activated
        if (sessionHelper.isInDedicatedActivateAccountFlow()) {
    		people = people.stream().filter(p -> !p.hasActivatedNSISUser() && p.isNsisAllowed()).collect(Collectors.toList());

    		if (people.isEmpty()) {
    			sessionHelper.clearSession();
    			return new ModelAndView("activateAccount/no-account-to-activate-error");
    		}
    	}

        sessionHelper.setAvailablePeople(people);

        model.addAttribute("people", people);

        return new ModelAndView("select-user", model.asMap());
    }

    public ModelAndView initiateNemIDOnlyLogin(Model model, HttpServletRequest httpServletRequest, RequireNemIdReason reason) {
    	return initiateNemIDOnlyLogin(model, httpServletRequest, reason, "");
    }

    public ModelAndView initiateNemIDOnlyLogin(Model model, HttpServletRequest httpServletRequest, RequireNemIdReason reason, String errCode) {
        nemIDService.populateModel(model, httpServletRequest);
        model.addAttribute("reason", reason);
        model.addAttribute("errCode", errCode);

        return new ModelAndView("login-nemid-only", model.asMap());
    }

    public ModelAndView initiateMFA(Model model, Person person, NSISLevel requiredNSISLevel) {
        List<MfaClient> clients = mfaService.getClients(person.getCpr());
        if (clients == null) {
            return new ModelAndView("error-could-not-get-mfa-devices");
        }

        clients = clients.stream().filter(client -> requiredNSISLevel.equalOrLesser(client.getNsisLevel())).collect(Collectors.toList());

        if (clients.size() == 0) {
            return new ModelAndView("error-no-mfa-devices");
        }
        sessionHelper.setMFAClients(clients);
        sessionHelper.setMFAClientRequiredNSISLevel(requiredNSISLevel);

        if (clients.size() == 1) {
            String deviceId = clients.get(0).getDeviceId();
            return new ModelAndView("redirect:/sso/saml/mfa/" + deviceId, model.asMap());
        }
        else {
            clients.sort(Comparator.comparing(MfaClient::getDeviceId));
            model.addAttribute("clients", clients);

            return new ModelAndView("login-mfa", model.asMap());
        }
    }

    public ModelAndView initiateApproveConditions(Model model) {
        sessionHelper.setInApproveConditionsFlow(true);
        model.addAttribute("terms", termsAndConditionsService.getTermsAndConditions().getContent());
        model.addAttribute("privacy", privacyPolicyService.getPrivacyPolicy().getContent());
        return new ModelAndView("approve-conditions", model.asMap());
    }

    public ModelAndView initiateActivateNSISAccount(Model model) {
        return initiateActivateNSISAccount(model, true);
    }

    public ModelAndView initiateActivateNSISAccount(Model model, boolean promptFirst) {
        sessionHelper.setInActivateAccountFlow(true);

        if (promptFirst) {
            return new ModelAndView("activateAccount/activate-prompt", model.asMap());
        }
        else {
            return new ModelAndView("redirect:/konto/aktiver");
        }
    }

    public ModelAndView initiatePasswordExpired(Person person, Model model) {
        PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
        if (person.hasActivatedNSISUser() && settings.isForceChangePasswordEnabled()) {
            if (person.getNsisPasswordTimestamp() == null) {
                log.warn("Person: " + person.getUuid() + " has no NSIS Password timestamp");
                
                // force a password change here - they skipped picking a password during initial enrollment
                model.addAttribute("forced", true);
                model.addAttribute("daysLeft", 0);
                model.addAttribute("alternativeLink", passwordSettingService.getSettings(person.getDomain()).getAlternativePasswordChangeLink());
                return new ModelAndView("password-expiry-prompt", model.asMap());
            }

            LocalDateTime expiredTimestamp = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());
            LocalDateTime almostExpiredTimestamp = expiredTimestamp.plusDays(7); // Should maybe be configurable later

            if (person.getNsisPasswordTimestamp().isBefore(almostExpiredTimestamp)) {
                model.addAttribute("forced", person.getNsisPasswordTimestamp().isBefore(expiredTimestamp));
                model.addAttribute("daysLeft", ChronoUnit.DAYS.between(expiredTimestamp, person.getNsisPasswordTimestamp()));
                model.addAttribute("alternativeLink", passwordSettingService.getSettings(person.getDomain()).getAlternativePasswordChangeLink());
                return new ModelAndView("password-expiry-prompt", model.asMap());
            }
        }

        return null;
    }

    public ModelAndView initiateForceChangePassword(Person person, Model model) {
        if (person.isForceChangePassword()) {
            return new ModelAndView("password-force-change-prompt", model.asMap());
        }
        return null;
    }

    public ModelAndView initiateSelectClaims(Person person, Model model, ServiceProvider serviceProvider) {
        if (sessionHelper.isInSelectClaimsFlow()) {
            return null;
        }

        if (person == null || person.getAttributes() == null || person.getAttributes().isEmpty()) {
            return null;
        }

        if (!(serviceProvider instanceof SqlServiceProvider)) {
            return null;
        }

        SqlServiceProvider sqlSP = (SqlServiceProvider) serviceProvider;
        if (serviceProvider == null || sqlSP.getRequiredFields() == null || sqlSP.getRequiredFields().isEmpty()) {
            return null;
        }

        // Determine the list of claims that the person has where they have multiple values associated with a key delimited by ;
        // and where the SP requires us to only return a single value
        Map<String, String> personAttributes = person.getAttributes();
        Map<String, ClaimValueDTO> toBeDecided = new HashMap<>();
        sqlSP.getRequiredFields().stream()
                .filter(SqlServiceProviderRequiredField::isSingleValueOnly)
                .filter(requiredField -> personAttributes.containsKey(requiredField.getPersonField()) && personAttributes.get(requiredField.getPersonField()).contains(";"))
                .forEach(requiredField -> {
                    PersonAttribute personAttribute = personAttributeService.getByName(requiredField.getPersonField());
                    String displayName = requiredField.getAttributeName();
                    if (personAttribute != null && StringUtils.hasLength(personAttribute.getDisplayName())) {
                        displayName = personAttribute.getDisplayName();
                    }

                    toBeDecided.put(requiredField.getAttributeName(), new ClaimValueDTO(displayName, new ArrayList<>(Arrays.asList(personAttributes.get(requiredField.getPersonField()).split(";")))));
                });

        // If no decisions about claims needs to be taken, continue without prompting the user
        if (toBeDecided.isEmpty()) {
            return null;
        }

        // Set Flow state, and clear previous values
        sessionHelper.setInSelectClaimsFlow(true);
        sessionHelper.setSelectedClaims(null);

        // Save claims to session for comparison to what the client returns later, for security/sanity checking
        sessionHelper.setSelectableClaims(toBeDecided);
        model.addAttribute("selectableClaims", toBeDecided);

        return new ModelAndView("login-select-claims");
    }

    private ModelAndView createAndSendAssertion(HttpServletResponse httpServletResponse, Person person, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
        AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

		// attempt to clear any residual incoming authnRequest, to avoid strange behaviour on
		// any following actions that might not be related to an authnRequest
		try {
			sessionHelper.setAuthnRequest(null);
		}
		catch (Exception ex) {
			; // ignore
		}

        try {
            Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
            spSessions.put(serviceProvider.getEntityId(), new HashMap<>());
            sessionHelper.setServiceProviderSessions(spSessions);

            // Create assertion
            MessageContext<SAMLObject> message = assertionService.createMessageContextWithAssertion(authnRequest, person);

            sessionHelper.setPassword(null);
            sessionHelper.refreshSession();

            loggingUtil.logResponse((Response) message.getMessage(), Constants.OUTGOING);

            // Send assertion
            HTTPPostEncoder encoder = new HTTPPostEncoder();
            encoder.setHttpServletResponse(httpServletResponse);
            encoder.setMessageContext(message);
            encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

            try {
                encoder.initialize();
                encoder.encode();
            }
            catch (ComponentInitializationException | MessageEncodingException e) {
                throw new ResponderException("Encoding error", e);
            }
        }
        catch (RequesterException ex) {
            errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
        }
        catch (ResponderException ex) {
            errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, ex);
        }

        return null;
    }

    public ModelAndView continueLoginWithMitIdOrNemId(Person person, NSISLevel authenticationLevel, @Nullable AuthnRequest authnRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws ResponderException, RequesterException {
        // Set authentication levels on the session
        sessionHelper.setPerson(person);
        sessionHelper.setPasswordLevel(authenticationLevel);
        sessionHelper.setMFALevel(authenticationLevel);
        sessionHelper.setAuthnInstant(new DateTime());

        // Dedicated Activate Account Flow: Go to "accept terms and conditions"-page
		if (sessionHelper.isInDedicatedActivateAccountFlow()) {
            // Any user who has gotten to this point correctly SHOULD not have this configuration
			if (person.hasActivatedNSISUser() || !person.isNsisAllowed()) {
				sessionHelper.clearSession();
				return new ModelAndView("activateAccount/no-account-to-activate-error");
			}

			return initiateApproveConditions(model);
		}

        // Has the user accepted the required conditions?
        if (requireApproveConditions(person)) {
            return initiateApproveConditions(model);
        }

        // Is the user allowed to get a NSIS User and do the already have one
        if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
            return initiateActivateNSISAccount(model, !sessionHelper.isInActivateAccountFlow());
        }

        if (sessionHelper.isInChoosePasswordResetOrUnlockAccountFlow()) {
        	return continueChoosePasswordResetOrUnlockAccount(model);
        }
        
      	// Password Change Flow: Go to "password change"-page
        if (sessionHelper.isInPasswordChangeFlow()) {
            return continueChangePassword(model);
        }

        // Check if person has authenticated with their ad password Before MitID/NemID login.
        // If they have, replicate the AD password to the nsis password field
        if (sessionHelper.isAuthenticatedWithADPassword() && person.hasActivatedNSISUser()) {
            Person adPerson = sessionHelper.getADPerson();
            if (adPerson != null && Objects.equals(adPerson.getId(), person.getId())) {
                if (StringUtils.hasLength(sessionHelper.getPassword())) {
                    try {
                        // We ignore the return value of changePassword because we bypass replication to AD
                        personService.changePassword(person, sessionHelper.getPassword(), true);
                        sessionHelper.setAuthenticatedWithADPassword(false);
                    }
                    catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
                        log.error("Kunne ikke kryptere password, password blev derfor ikke ændret", e);
                        throw new ResponderException("Der opstod en fejl i skift kodeord");
                    }
                }
            }
        }

        // Check if the users ForceChangePassword is set to true.
        // Usually happens if user has been manually activated
        if (person.isForceChangePassword()) {
            sessionHelper.setInForceChangePasswordFlow(true);
            return new ModelAndView("password-force-change-prompt", model.asMap());
        }

        // AuthnRequest required from here.
        // Other features does not necessarily require it
        if (authnRequest == null) {
            log.warn("No authnRequest found on session, redirecting to index page");
            return new ModelAndView("redirect:/");
        }

        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

        // the ServiceProvider might have additional requires of the person, before usage is allowed
        RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
        if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
            auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
            throw new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
        }

        // if the ServiceProvider requires NSIS LOW or above, extra checks required
        if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
            if (!person.isNsisAllowed()) {
                errorResponseService.sendResponderError(httpServletResponse, authnRequest, new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login"));
                return null;
            }
        }

        if (person.isLockedByOtherThanPerson()) {
        	return new ModelAndView(PersonService.getCorrectLockedPage(person));
        }

        return initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
    }

    public ModelAndView continueChoosePasswordResetOrUnlockAccount(Model model) throws RequesterException, ResponderException {
        // Make sure that the user is already in the process of changing their password otherwise deny.
        if (!sessionHelper.isInChoosePasswordResetOrUnlockAccountFlow()) {
            sessionHelper.clearSession();
            throw new RequesterException("Bruger tilgik vælg kodeordsskifte eller åben konto forkert, prøv igen");
        }

        Person person = sessionHelper.getPerson();
        if (person == null) {
            throw new ResponderException("Person var ikke gemt på session da fortsæt password skift blev tilgået");
        }

        PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
        String sAMAccountName = null;

        if (person.getSamaccountName() != null && settings.isReplicateToAdEnabled()) {
        	sAMAccountName = person.getSamaccountName();
        }
		
    	model.addAttribute("redirectUrl", sessionHelper.getPasswordChangeSuccessRedirect());
    	model.addAttribute("sAMAccountName", sAMAccountName);
    	
    	return new ModelAndView("changePassword/forgot-password-or-locked", model.asMap());
	}

	public ModelAndView continueChangePassword(Model model) throws RequesterException, ResponderException {
		return continueChangePassword(model, null);
	}

    public ModelAndView continueChangePassword(Model model, PasswordChangeForm form) throws RequesterException, ResponderException {
        // Make sure that the user is already in the process of changing their password otherwise deny.
        if (!sessionHelper.isInPasswordChangeFlow()) {
            sessionHelper.clearSession();
            throw new RequesterException("Bruger tilgik skift kodeord forkert, prøv igen");
        }

        Person person = sessionHelper.getPerson();
        if (person == null) {
            throw new ResponderException("Person var ikke gemt på session da fortsæt password skift blev tilgået");
        }

        PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
        String samaccountName = null;

        if (person.getSamaccountName() != null && settings.isReplicateToAdEnabled()) {
        	samaccountName = person.getSamaccountName();
        }
		
    	model.addAttribute("authenticatedWithNemId", sessionHelper.isAuthenticatedWithNemIdOrMitId());
        model.addAttribute("samaccountName", samaccountName);
        model.addAttribute("settings", settings);
        model.addAttribute("passwordForm", (form != null) ? form : new PasswordChangeForm());

        return new ModelAndView("changePassword/change-password", model.asMap());
    }

	public boolean requireApproveConditions(Person person) {
		if (!person.isApprovedConditions()) {
			return true;
		}

		if (person.getApprovedConditionsTts() == null) {
			return true;
		}

		LocalDateTime tts = termsAndConditionsService.getLastRequiredApprovedTts();
		if (tts == null) {
			return false;
		}

		if (person.getApprovedConditionsTts().isBefore(tts)) {
			return true;
		}

		return false;
	}
}
