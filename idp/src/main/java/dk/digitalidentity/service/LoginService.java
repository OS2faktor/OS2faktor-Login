package dk.digitalidentity.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.KnownNetworkService;
import dk.digitalidentity.common.service.LoginInfoMessageService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import dk.digitalidentity.service.serviceprovider.NemLoginServiceProvider;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.IPUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LoginService {

    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private ServiceProviderFactory serviceProviderFactory;

    @Autowired
    private LoginInfoMessageService loginInfoMessageService;

    @Autowired
    private PersonService personService;

    @Autowired
    private ErrorResponseService errorResponseService;

    @Autowired
    private ErrorHandlingService errorHandlingService;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private CommonConfiguration commonConfiguration;

    @Autowired
    private OS2faktorConfiguration configuration;

    @Autowired
    private OpenSAMLHelperService samlHelper;

    @Autowired
    private FlowService flowService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private KnownNetworkService knownNetworkService;

    /**
     * This method handles receiving any login request, and will then determine based on session what to do with the user
     */
    public ModelAndView loginRequestReceived(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model, LoginRequest loginRequest) throws RequesterException, ResponderException  {
        // Clear any flow states in session so user does not end up in a bad state with a new AuthnRequest
        sessionHelper.clearFlowStates();

        sessionHelper.setLoginRequest(loginRequest);

        // If we have an IP stored on session we require the user to use the same one,
        // if a new IP is used for the same session the users login state is blanked and login continues from that point
        boolean valid = sessionHelper.handleValidateIP();

        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);
        if (!serviceProvider.getProtocol().equals(loginRequest.getProtocol())) {
            throw new RequesterException("Kunne ikke gennemføre login da Tjenesteudbyderens protokol ikke matcher den modtagne login forespørgsel");
        }

        switch (loginRequest.getProtocol()) {
            case SAML20:
                auditLogger.authnRequest(sessionHelper.getPerson(), samlHelper.prettyPrint(loginRequest.getAuthnRequest()), serviceProvider.getName(loginRequest));
                break;
            case OIDC10:
                OAuth2AuthorizationCodeRequestAuthenticationToken token = loginRequest.getToken();
                auditLogger.oidcAuthorizationRequest(sessionHelper.getPerson(), OidcAuthCodeRequestService.tokenToString(token), serviceProvider.getName(loginRequest));
                break;
            case WSFED:
                auditLogger.wsFederationLoginRequest(sessionHelper.getPerson(), serviceProvider.getName(loginRequest), loginRequest.getWsFedLoginParameters());
            case ENTRAMFA:
            	throw new RequesterException("EntraID MFA login flow er startet forkert");
        }

        NSISLevel loginState = sessionHelper.getLoginState(serviceProvider, loginRequest);

        // a ServiceProvider can be configured to just proxy straight to NemLog-in, skipping the build-in IdP login mechanisms.
        // In this case we will always just forward the request, and ignore any existing sessions, as NemLog-in is required here
        // Start login flow against NemLog-in no matter the session
        if (commonConfiguration.getNemlogin().isBrokerEnabled() && (serviceProvider.nemLogInBrokerEnabled()) || loginRequest.isRequireBrokering()) {
            sessionHelper.setInNemLogInBrokerFlow(true);

            if (serviceProvider.isAllowMitidErvhervLogin()) {
            	sessionHelper.setRequestProfessionalProfile();
            	sessionHelper.setRequestPersonalProfile();
            }
            else {
            	sessionHelper.setRequestPersonalProfile();
            }

            return new ModelAndView("redirect:/nemlogin/saml/login");
        }
        
        if (commonConfiguration.getMitIdErhverv().isEnabled()) {
        	sessionHelper.setRequestProfessionalProfile();
        }

        // if forceAuthn and MFA required we need to force a new MFA auth
        if (loginRequest.isForceAuthn() && serviceProvider.mfaRequired(loginRequest, null, IPUtil.isIpInTrustedNetwork(knownNetworkService.getAllIPs(), httpServletRequest))) {
            sessionHelper.setMFALevel(null);
        }

        // If forceAuthn is required,
        // you're not logged in,
        // or you're accessing the service from a new IP regardless of previous authentication
        // you will be asked login
        if (loginRequest.isForceAuthn() || loginState == null || !valid) {
            if (loginRequest.isPassive()) {
                throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
            }

            return initiateLogin(model, httpServletRequest, serviceProvider.preferNemId());
        }

        // Login state is non-null which means we have already determined a person on the session
        Person person = sessionHelper.getPerson();

        // if the SP requires NSIS LOW or above, extra checks required
        if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(loginRequest))) {
            // is user even allowed to login to NSIS applications
            if (!person.isNsisAllowed()) {
                throw new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
            }

            // has the user approved conditions?
            if (personService.requireApproveConditions(person)) {
                if (loginRequest.isPassive()) {
                    throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
                }
                else {
                    return flowService.initiateApproveConditions(model);
                }
            }

            // has the user activated their NSIS User?
            if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
                if (!loginRequest.isPassive()) {
                    return flowService.initiateActivateNSISAccount(model);
                }
            }
        }

        // for non-selfservice service providers we have additional constraints
        if (person.isLockedByOtherThanPerson()) {
            if (loginRequest.isPassive()) {
                throw new ResponderException("Kunne ikke gennemføre passivt login da brugerens konto er låst");
            }
            else {
                return new ModelAndView(PersonService.getCorrectLockedPage(person));
            }
        }

        return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
    }

    /**
     * This method is called after the person requesting login has been determined.
     */
    public ModelAndView continueLoginFlow(Person person, String username, String password, LoginRequest loginRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws RequesterException, ResponderException {
        // Check that AuthnRequest is present and fetch the ServiceProvider by the AuthnRequest
        // 	This check is done before validating password, Fetching the service provider can fail because the SP is not supported
        // 	This error would not give any information about the user that is in the process of login.
        if (loginRequest == null) {
            log.warn("No loginRequest found on session");
            return new ModelAndView("redirect:/");
        }
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);

        // Check password
        boolean badPasswordMustBeChanged = false;
        PasswordValidationResult passwordValidationResult = passwordService.validatePassword(password, person);
        switch (passwordValidationResult) {
        	case VALID_BUT_BAD_PASWORD:
        		badPasswordMustBeChanged = true;
        		// fallthrough to VALID case on purpose
            case VALID:
                personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword(), false);
                break;
            case VALID_EXPIRED:
                personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword(), true);
                break;
            case LOCKED:
                return new ModelAndView("error-password-locked");
            case INVALID_BAD_PASSWORD:
    			model.addAttribute("reason", person.getBadPasswordReason().toString());
    			return new ModelAndView("error-password-bad", model.asMap());
            case INVALID:
                // password was invalid, so we check if they have not locked themselves out of their account,
                // otherwise we just return them to the login prompt
                if (person.isLocked()) {
                    return new ModelAndView(PersonService.getCorrectLockedPage(person));
                }
                else {
                    return initiateLogin(model, httpServletRequest, false, true, (username != null ? username : ""));
                }
            case INSUFFICIENT_PERMISSION:
            case TECHNICAL_ERROR:
                try {
                    errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException("Der opstod en teknisk fejl i forbindelse med login"));
                    return null;
                }
                catch (RequesterException | ResponderException ex) {
                    return errorHandlingService.modelAndViewError(httpServletRequest.getRequestURI(), httpServletRequest, "Der opstod en teknisk fejl i forbindelse med login", model, true);
                }
        }

        // Remember the person on session since we now know who they are confirmed by username/password
        sessionHelper.setPerson(person);

        // Check if the person meets the requirements of the ServiceProvider specified in the AuthnRequest
        RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
        if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
            auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
            ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
            errorResponseService.sendError(httpServletResponse, loginRequest, e);
            return null;
        }

        // If we have to re-authenticate MFA: Instead of starting MFA we clear any previous MFA Level,
        // and let login service handle mfa since it chooses between multiple options
        if (loginRequest.isForceAuthn() && serviceProvider.mfaRequired(loginRequest, person.getDomain(), IPUtil.isIpInTrustedNetwork(knownNetworkService.getAllIPs(), httpServletRequest))) {
            sessionHelper.setMFALevel(null);
        }

        // Check if locked
        if (person.isLockedByOtherThanPerson()) {
            return new ModelAndView(PersonService.getCorrectLockedPage(person));
        }

		// check if the persons password needs to be changed, note that this includes checking if the person has forceChangePassword = true
		// also note that the last argument is false (perform expires-soon-check), as we want to query the user for a password change here
        ModelAndView modelAndView = flowService.initiatePasswordExpired(person, model, false);
        if (modelAndView != null) {
            sessionHelper.setPassword(password);
            return modelAndView;
        }
        
        if (badPasswordMustBeChanged) {
        	// need to store it for changing password / skipping change password flow
			sessionHelper.setPassword(password);
			// this allows the user to change password without having to use MitID
			sessionHelper.setInPasswordExpiryFlow(true);

			model.addAttribute("reason", (person.getBadPasswordReason() != null) ? person.getBadPasswordReason().toString() : "");
			model.addAttribute("deadline", person.getBadPasswordDeadlineTts());

			return new ModelAndView("bad-password-prompt", model.asMap());
        }
        
        // If the SP requires NSIS LOW or above, extra checks required
        if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(loginRequest))) {
            if (!person.isNsisAllowed()) {
                throw new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login", "Ingen Erhvervsidentitet", "cms.login-error.nsis-not-allowed");
            }

            // Has the user approved conditions?
            if (personService.requireApproveConditions(person)) {
                return flowService.initiateApproveConditions(model);
            }

            // Has the user activated their NSIS User?
            if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
                if (sessionHelper.isAuthenticatedWithADPassword()) {
                    sessionHelper.setPassword(password);
                }
                return flowService.initiateActivateNSISAccount(model);
            }
        }

        return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
    }

    public ModelAndView continueLoginAfterForceChangePassword(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws RequesterException, ResponderException {
        if (!sessionHelper.isInForceChangePasswordFlow()) {
            sessionHelper.clearSession();
            throw new RequesterException("Bruger tilgik en url de ikke havde adgang til, prøv igen");
        }

        sessionHelper.setInForceChangePasswordFlow(false); // allows only onetime access

        Person person = sessionHelper.getPerson();

        // Check if locked
        if (person.isLockedByOtherThanPerson()) {
            return new ModelAndView(PersonService.getCorrectLockedPage(person));
        }

        // Has the user approved conditions?
        if (personService.requireApproveConditions(person)) {
            return flowService.initiateApproveConditions(model);
        }

        // Has the user activated their NSIS User?
        if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
            return flowService.initiateActivateNSISAccount(model);
        }
        
        // Reaching this endpoint the person have already authenticated with their manually activated password or NemID,
        // so we just continue login flow from here
        return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
    }

    public ModelAndView continueLoginChangePasswordDeclined(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws RequesterException, ResponderException {
        Person person = sessionHelper.getPerson();
        if (person == null) {
            // it seems weird to ever get here without a person on the session, but the endpoint can be accessed directly,
            // so a NPE could be thrown if we do not check for null here.
            log.warn("No person found on session");
            return new ModelAndView("redirect:/");
        }

        sessionHelper.setInPasswordExpiryFlow(false);

        // Check if locked
        if (person.isLockedByOtherThanPerson()) {
            return new ModelAndView(PersonService.getCorrectLockedPage(person));
        }

        // Has the user approved conditions?
        if (personService.requireApproveConditions(person)) {
            return flowService.initiateApproveConditions(model);
        }

        // Has the user activated their NSIS User?
        if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
            return flowService.initiateActivateNSISAccount(model);
        }

        // Check password
        String password = sessionHelper.getPassword();
        sessionHelper.setPassword(null);

        PasswordValidationResult passwordValidationResult = passwordService.validatePassword(password, person);
        switch (passwordValidationResult) {
            case VALID:
            case VALID_BUT_BAD_PASWORD: // they declined password change, so continue for now
                return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
            case INVALID_BAD_PASSWORD:
    			model.addAttribute("reason", person.getBadPasswordReason().toString());
    			return new ModelAndView("error-password-bad", model.asMap());
            case INVALID:
            case VALID_EXPIRED:
                if (person.isLockedByOtherThanPerson()) {
                    return new ModelAndView(PersonService.getCorrectLockedPage(person));
                }
                else {
                    return initiateLogin(model, httpServletRequest, false, true, "");
                }
            case LOCKED:
                return new ModelAndView("error-password-locked");
            case TECHNICAL_ERROR:
            case INSUFFICIENT_PERMISSION:
                try {
                    LoginRequest loginRequest = sessionHelper.getLoginRequest();
                    errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException("Der opstod en teknisk fejl i forbindelse med login"));
                    return null;
                }
                catch (Exception ex) {
                    return errorHandlingService.modelAndViewError(httpServletRequest.getRequestURI(), httpServletRequest, "Der opstod en teknisk fejl i forbindelse med login", model, true);
                }
        }
        
        // will not reach this point as long as ALL cases are handled above
        return null;
    }

    public List<Person> getPeople(String username) {
        if (!StringUtils.hasLength(username)) {
            return new ArrayList<>();
        }

        List<Person> result = new ArrayList<>();

        String shortUsername = username;

        // allow users to input username with domain, for now disregard domain
        if (shortUsername.contains("@")) {
            String[] split = shortUsername.split("@");
            shortUsername = split[0];
        }

        // Get all persons with SAMAccountName equals to provided username
        result.addAll(personService.getBySamaccountName(shortUsername));

        if (configuration.isLoginWithUpn()) {
            result.addAll(personService.getByUPN(username));
        }
        
        return result.stream().distinct().collect(Collectors.toList());
    }

    public ModelAndView initiateLogin(Model model, HttpServletRequest request, boolean preferNemid) {
        return initiateLogin(model, request, preferNemid, false, null);
    }

    public ModelAndView initiateLogin(Model model, HttpServletRequest request, boolean preferNemid, boolean incorrectInput, String username) {
        model.addAttribute("infobox", loginInfoMessageService.getInfobox());

        String changePasswordUrl = "/sso/saml/forgotpworlocked";
        if (StringUtils.hasLength(request.getQueryString())) {
            String params = URLEncoder.encode("/sso/saml/login?" + request.getQueryString(), StandardCharsets.UTF_8);
            changePasswordUrl += "?redirectUrl=" + params;
        }
        model.addAttribute("changePasswordUrl", changePasswordUrl);

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

        // hack to support embedded IE browsers
        try {
	        String ua = request.getHeader("user-agent").toLowerCase();
	        if (ua.indexOf("trident") >= 0 || ua.indexOf("edge/") >= 0) {
	            return new ModelAndView("login-ie", model.asMap());
	        }
        }
        catch (Exception ignored) {
        	;
        }
        
        return new ModelAndView("login", model.asMap());
    }

    public boolean showNemLogIn() {
        if (commonConfiguration.getNemlogin().isBrokerEnabled()) {
        	// we should prevent showing MitID if the request is from NemLog-in, as NL3 gets confused with 2 parallel logins
            // Try to determine if ServiceProvider is the SelfService since it is always allowed to use NemLog-in
            try {
                LoginRequest loginRequest = sessionHelper.getLoginRequest();
                if (loginRequest != null) {
                    ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);
                    if (serviceProvider != null && Objects.equals(NemLoginServiceProvider.SP_NAME, serviceProvider.getName(null))) {
                        return false;
                    }
                }
            }
            catch (RequesterException | ResponderException ex) {
                log.warn("Could not determine where user is trying to login to", ex);
            }
        	
        	// otherwise show it
            return true;
        }
        else {
            // Try to determine if ServiceProvider is the SelfService since it is always allowed to use NemLog-in
            try {
                LoginRequest loginRequest = sessionHelper.getLoginRequest();
                if (loginRequest != null) {
                    ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);
                    if (serviceProvider != null && serviceProvider instanceof SelfServiceServiceProvider) {
                        return true;
                    }
                }
            }
            catch (RequesterException | ResponderException ex) {
                log.warn("Could not determine where user is trying to login to", ex);
            }

            // if broker is not enabled, default to deny access if we can't determine ServiceProvider OR it is not SelfService
            return false;
        }
    }

    public ModelAndView continueLoginWithMitIdOrNemId(Person person, NSISLevel authenticationLevel, @Nullable LoginRequest loginRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws ResponderException, RequesterException {
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

			return flowService.initiateApproveConditions(model);
		}

        // Has the user accepted the required conditions?
        if (personService.requireApproveConditions(person)) {
            return flowService.initiateApproveConditions(model);
        }

        // Is the user allowed to get a NSIS User and do the already have one
        if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
            return flowService.initiateActivateNSISAccount(model, !sessionHelper.isInActivateAccountFlow());
        }

        if (sessionHelper.isInChoosePasswordResetOrUnlockAccountFlow()) {
        	return flowService.continueChoosePasswordResetOrUnlockAccount(model);
        }
        
      	// Password Change Flow: Go to "password change"-page
        if (sessionHelper.isInPasswordChangeFlow()) {
            return flowService.continueChangePassword(model);
        }

        // Check if person has authenticated with their ad password Before MitID/NemID login.
        // If they have, replicate the AD password to the nsis password field
        if (sessionHelper.isAuthenticatedWithADPassword() && person.hasActivatedNSISUser()) {
            Person adPerson = sessionHelper.getADPerson();
            if (adPerson != null && Objects.equals(adPerson.getId(), person.getId())) {
                if (StringUtils.hasLength(sessionHelper.getPassword())) {
                    try {
                        // We ignore the return value of changePassword because we bypass replication to AD
                        personService.changePassword(person, sessionHelper.getPassword(), true, null, null, false);
                        sessionHelper.setAuthenticatedWithADPassword(false);
                    }
                    catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
                        log.error("Kunne ikke kryptere password, password blev derfor ikke ændret", e);
                        throw new ResponderException("Der opstod en fejl i skift kodeord");
                    }
                }
            }
        }

        // Check if password expired or forceChangePassword = true
        ModelAndView modelAndView = flowService.initiatePasswordExpired(person, model, false);
        if (modelAndView != null) {
            return modelAndView;
        }

        // AuthnRequest required from here.
        // Other features does not necessarily require it
        if (loginRequest == null) {
            log.warn("No LoginRequest found on session, redirecting to index page");
            return new ModelAndView("redirect:/");
        }

        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);

        // the ServiceProvider might have additional requires of the person, before usage is allowed
        RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
        if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
            auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
            throw new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
        }

        // if the ServiceProvider requires NSIS LOW or above, extra checks required
        if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(loginRequest))) {
            if (!person.isNsisAllowed()) {
                errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login"));
                return null;
            }
        }

        if (person.isLockedByOtherThanPerson()) {
        	return new ModelAndView(PersonService.getCorrectLockedPage(person));
        }

        return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
    }
}
