package dk.digitalidentity.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.LoginInfoMessageService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
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

    public ModelAndView initiateFlowOrCreateAssertion(Model model, HttpServletResponse response, HttpServletRequest request, Person person) throws ResponderException, RequesterException {
        ResponderException cannotPerformPassiveLogin = new ResponderException("Passiv login krævet, men bruger er ikke logget ind på det krævede niveau");

        AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
        NSISLevel currentNSISLevel = sessionHelper.getLoginState();
        NSISLevel requiredNSISLevel = serviceProvider.nsisLevelRequired(authnRequest);

        if (requiredNSISLevel == NSISLevel.HIGH) {
            throw new ResponderException("Understøtter ikke NSIS Høj");
        }

        // If no login state, initiate login
        if (currentNSISLevel == null) {
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

        // Has the user approved conditions?
        if (!person.isApprovedConditions()) {
            if (authnRequest.isPassive()) {
                throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
            }
            else {
                return initiateApproveConditions(model);
            }
        }

        // Has the user activated their NSIS User?
        boolean declineUserActivation = sessionHelper.isDeclineUserActivation();
        if (!declineUserActivation && person.isNsisAllowed() && !person.hasNSISUser()) {
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
	        switch (currentNSISLevel) {
	            case SUBSTANTIAL:
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
	                        throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der bruger NSIS Lav");
	                    }

                        return initiateMFA(model, person, NSISLevel.SUBSTANTIAL);
	                }
	
	                return createAndSendAssertion(response, person, serviceProvider);
	            case NONE:
                    if (authnRequest.isPassive()) {
                        throw cannotPerformPassiveLogin;
                    }

                    if (!NSISLevel.LOW.equalOrLesser(person.getNsisLevel())) {
                        throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der ikke bruger NSIS");
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

            return initiateMFA(model, person, NSISLevel.NONE);
        }

        // user is already logged in at the required level
        return createAndSendAssertion(response, person, serviceProvider);
    }

    public boolean validPassword(String password, Person person) {
        return validPassword(password, person, false);
    }

    private boolean validPassword(String password, Person person, boolean allowExpiredPassword) {
        if (StringUtils.isEmpty(password)) {
            sessionHelper.setPasswordLevel(null);
            return false;
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());

        // NSIS Password validation
        if (!StringUtils.isEmpty(person.getNsisPassword())) {
            if (!allowExpiredPassword && settings.isForceChangePasswordEnabled() && person.getNsisPasswordTimestamp() != null) {
                LocalDateTime maxPasswordAge = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());
                if (person.getNsisPasswordTimestamp().isAfter(maxPasswordAge)) {
                    if (encoder.matches(password, person.getNsisPassword())) {
                        sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
                        return true;
                    }
                }
            }
            else if (encoder.matches(password, person.getNsisPassword())) {
                sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
                return true;
            }
        }

        if (!StringUtils.isEmpty(person.getSamaccountName())) {
            // Local AD password validation
            LocalDateTime maxRetention = LocalDateTime.now().minusDays(settings.getCacheAdPasswordInterval());
            if (!StringUtils.isEmpty(person.getAdPassword()) && person.getAdPasswordTimestamp() != null && person.getAdPasswordTimestamp().isAfter(maxRetention)) {
                if (encoder.matches(password, person.getAdPassword())) {
                    sessionHelper.setAuthenticatedWithADPassword(true);
                    sessionHelper.setADPerson(person);
                    sessionHelper.setPasswordLevel(NSISLevel.NONE);
                    return true;
                }
            }

            // Remote AD password validation
            if (adPasswordService.validatePassword(person, password)) {
                String encodedPassword = encoder.encode(password);
                person.setAdPassword(encodedPassword);
                person.setAdPasswordTimestamp(LocalDateTime.now());
                personService.save(person);

                sessionHelper.setAuthenticatedWithADPassword(true);
                sessionHelper.setADPerson(person);
                sessionHelper.setPasswordLevel(NSISLevel.NONE);
                return true;
            }
        }

        sessionHelper.setPasswordLevel(null);
        return false;
    }

    public List<Person> getPeople(String username) {
        if (StringUtils.isEmpty(username)) {
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
        model.addAttribute("changePasswordUrl", "/sso/saml/changepassword?redirectUrl=" + params);

        if (incorrectInput) {
            model.addAttribute("incorrectInput", true);
            model.addAttribute("preferNemid", false);
            model.addAttribute("username", username);
        }
        else {
            model.addAttribute("preferNemid", preferNemid);
        }

        return new ModelAndView("login", model.asMap());
    }

    public ModelAndView initiateUserSelect(Model model, List<Person> people, boolean nemIdAuthenticated) {
        sessionHelper.setAuthenticatedWithNemId(nemIdAuthenticated);
        sessionHelper.setAvailablePeople(people);

        model.addAttribute("people", people);
        return new ModelAndView("select-user", model.asMap());
    }

    public ModelAndView initiateNemIDOnlyLogin(Model model, HttpServletRequest httpServletRequest, RequireNemIdReason reason) {
        nemIDService.populateModel(model, httpServletRequest);
        model.addAttribute("reason", reason);

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
        return new ModelAndView("approve-conditions", model.asMap());
    }

    public ModelAndView initiateActivateNSISAccount(Model model) {
        return initiateActivateNSISAccount(model, true);
    }

    public ModelAndView initiateActivateNSISAccount(Model model, boolean promptFirst) {
        sessionHelper.setInActivateAccountFlow(true);

        if (promptFirst) {
            return new ModelAndView("activateAccount/activate-prompt", model.asMap());
        } else {
            return new ModelAndView("redirect:/konto/aktiver");
        }
    }

    public ModelAndView initiatePasswordExpired(Person person, Model model) {
        PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
        if (person.hasNSISUser() && settings.isForceChangePasswordEnabled()) {
            if (person.getNsisPasswordTimestamp() == null) {
                log.error("Person: " + person.getUuid() + " has no NSIS Password timestamp");
                return null;
            }

            LocalDateTime expiredTimestamp = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());
            LocalDateTime almostExpiredTimestamp = expiredTimestamp.plusDays(7); // Should maybe be configurable later

            if (person.getNsisPasswordTimestamp().isBefore(almostExpiredTimestamp)) {
                model.addAttribute("forced", person.getNsisPasswordTimestamp().isBefore(expiredTimestamp));
                model.addAttribute("daysLeft", ChronoUnit.DAYS.between(expiredTimestamp, person.getNsisPasswordTimestamp()));
                return new ModelAndView("password-expiry-prompt", model.asMap());
            }
        }

        return null;
    }

    private ModelAndView createAndSendAssertion(HttpServletResponse httpServletResponse, Person person, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
        AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
        sessionHelper.setAuthenticatedWithNemId(null);
        sessionHelper.setPassword(null);

        try {
            Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
            spSessions.put(serviceProvider.getEntityId(), new HashMap<>());
            sessionHelper.setServiceProviderSessions(spSessions);

            // Create assertion
            MessageContext<SAMLObject> message = assertionService.createMessageContextWithAssertion(authnRequest, person);

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

    // TODO this logic is now almost the exact same as in NemIdController should probably be merged
    public ModelAndView continueNemdIDLogin(Model model, HttpServletResponse response, HttpServletRequest request) throws ResponderException, RequesterException {
        AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

        if (!(sessionHelper.isAuthenticatedWithNemId() && sessionHelper.getPerson() != null)) {
            errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new ResponderException("Brugeren tilgik den alternative nemid login uden at have været igennem det normale authentification"));
            return null;
        }

        Person person = sessionHelper.getPerson();

        // Check if person has authenticated with their ad password. If they have, replicate the AD password to the nsis password field
        if (sessionHelper.isAuthenticatedWithADPassword() && person.hasNSISUser()) {
            Person adPerson = sessionHelper.getADPerson();
            if (adPerson != null && Objects.equals(adPerson.getId(), person.getId())) {
                if (!StringUtils.isEmpty(person.getAdPassword())) {
                    try {
                        personService.changePassword(person, person.getAdPassword(), false, true);
                        sessionHelper.setAuthenticatedWithADPassword(false);
                    } catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
                        log.error("Kunne ikke kryptere password, password blev derfor ikke ændret", e);
                        throw new ResponderException("Der opstod en fejl i skift kodeord");
                    }
                }
            }
        }

        // Set authentication levels
        sessionHelper.setPerson(person);
        sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
        sessionHelper.setMFALevel(NSISLevel.SUBSTANTIAL);

        // If the SP requires NSIS LOW or above, extra checks required
        if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
            if (!person.isNsisAllowed()) {
                throw new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
            }
        }

        // Has the user accepted the required conditions?
        if (!person.isApprovedConditions()) {
            return initiateApproveConditions(model);
        }

        // Is the user allowed to get a NSIS User and do the already have one
        if (person.isNsisAllowed() && !person.hasNSISUser()) {
            return initiateActivateNSISAccount(model, !sessionHelper.isInActivateAccountFlow());
        }


        // If trying to login to anything else than selfservice check if person is locked
        if (!(serviceProvider instanceof SelfServiceServiceProvider)) {
            if (person.isLocked()) {
                return new ModelAndView("error-locked-account");
            }
        }

        return initiateFlowOrCreateAssertion(model, response, request, person);
    }

    public ModelAndView continueChangePassword(Model model) throws RequesterException, ResponderException {
        // Make sure that the user is already in the process of changing their password otherwise deny.
        if (!sessionHelper.isInPasswordChangeFlow()) {
            sessionHelper.clearSession();
            throw new RequesterException("Bruger tilgik skift kodeord forkert, prøv igen");
        }

        Person person = sessionHelper.getPerson();
        if (person == null) {
            throw new ResponderException("Person var ikke gemt på session da fortsæt password skift blev tilgået");
        }

        model.addAttribute("settings", passwordSettingService.getSettings(person.getDomain()));
        model.addAttribute("passwordForm", new PasswordChangeForm());

        return new ModelAndView("changePassword/change-password");
    }
}
