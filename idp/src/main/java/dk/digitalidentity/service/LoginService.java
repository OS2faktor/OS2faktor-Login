package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.LoginInfoMessageService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;

@Service
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
    private TermsAndConditionsService termsAndConditionsService;

    public ModelAndView initiateFlowOrCreateAssertion(Model model, HttpServletResponse response, HttpServletRequest request, Person person) throws ResponderException, RequesterException {
        // Prerequisites
        AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
        ResponderException cannotPerformPassiveLogin = new ResponderException("Passiv login krævet, bruger havde ikke opnået det krævede niveau");
        NSISLevel currentNSISLevel = sessionHelper.getLoginState();
        NSISLevel requiredNSISLevel = serviceProvider.nsisLevelRequired(authnRequest);

        if (requiredNSISLevel == NSISLevel.HIGH) {
            throw new ResponderException("Undersøtter ikke NSIS Høj");
        }

        // IF no login state, initiate login
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

        // If logging in with NSIS LOW or above, conditions must be approved
        if (!person.isApprovedConditions() && NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
            if (authnRequest.isPassive()) {
                throw cannotPerformPassiveLogin;
            }

            return initiateNemIDOnlyLogin(model, request, RequireNemIdReason.TERMS_AND_CONDITIONS);
        }

        switch (currentNSISLevel) {
            case SUBSTANTIAL:
                return createAndSendAssertion(response, person, serviceProvider);
            case LOW:
                // Checks if SP requires higher level than LOW, and tires to give user that level
                if (NSISLevel.SUBSTANTIAL.equalOrLesser(requiredNSISLevel)) {
                    if (authnRequest.isPassive()) {
                        throw cannotPerformPassiveLogin;
                    }

                    if (authnRequestService.requireNemId(authnRequest)) {
                        initiateNemIDOnlyLogin(model, request, null);
                    }

                    if (!NSISLevel.SUBSTANTIAL.equalOrLesser(person.getNsisLevel())) {
                        throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der bruger NSIS Lav");
                    }

                    if (!NSISLevel.SUBSTANTIAL.equalOrLesser(sessionHelper.getMFALevel())) {
                        return initiateMFA(model, person, NSISLevel.SUBSTANTIAL);
                    }
                }

                return createAndSendAssertion(response, person, serviceProvider);
            case NONE:
                // Checks if SP requires higher level than NONE, and tires to give user that level
                if (NSISLevel.LOW.equalOrLesser(requiredNSISLevel)) {
                    if (authnRequest.isPassive()) {
                        throw cannotPerformPassiveLogin;
                    }

                    if (!NSISLevel.LOW.equalOrLesser(person.getNsisLevel())) {
                        throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der ikke bruger NSIS");
                    }

                    RequireNemIdReason reason = authnRequestService.requireNemId(authnRequest) ? null : RequireNemIdReason.AD;
                    return initiateNemIDOnlyLogin(model, request, reason);
                }

                if (serviceProvider.mfaRequired(authnRequest) && !NSISLevel.NONE.equalOrLesser(sessionHelper.getMFALevel())) {
                    if (authnRequest.isPassive()) {
                        throw cannotPerformPassiveLogin;
                    }

                    return initiateMFA(model, person, NSISLevel.NONE);
                }

                return createAndSendAssertion(response, person, serviceProvider);
            default:
                sessionHelper.clearSession();
                if (authnRequestService.requireNemId(authnRequest)) {
                    return initiateNemIDOnlyLogin(model, request, null);
                }
                return initiateLogin(model, request, serviceProvider.preferNemId());
        }
    }

    public boolean validPassword(String password, Person person) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // NSIS Password validation
        if (!StringUtils.isEmpty(person.getNsisPassword())) {
            if (encoder.matches(password, person.getNsisPassword())) {
                sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
                return true;
            }
        }

        if (!StringUtils.isEmpty(person.getSamaccountName())) {
            // Local AD password validation
            if (!StringUtils.isEmpty(person.getAdPassword())) {
                if (encoder.matches(password, person.getAdPassword())) {
                    sessionHelper.setAuthenticatedWithADPassword(true);
                    sessionHelper.setADPerson(person);
                    sessionHelper.setPasswordLevel(NSISLevel.NONE);
                    return true;
                }
            }

            // Remote AD password validation
            // TODO AD Integration setting (NSIS-107)
            if (adPasswordService.validatePassword(person, password)) {
                String encodedPassword = encoder.encode(password);
                person.setAdPassword(encodedPassword);
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

    public ModelAndView initiateAcceptTerms(Model model) {
        model.addAttribute("terms", termsAndConditionsService.getTermsAndConditions().getContent());
        return new ModelAndView("activateAccount/activate-accept", model.asMap());
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

    //TODO this logic is now almost the exact same as in NemIdController should probably be merged
    public ModelAndView continueNemdIDLogin(Model model, HttpServletResponse response, HttpServletRequest request) throws ResponderException, RequesterException {
        if (!(sessionHelper.isAuthenticatedWithNemId() && sessionHelper.getPerson() != null)) {
            AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
            errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new ResponderException("Brugeren tilgik den alternative nemid login uden at have været igennem det normale authentification"));
            return null;
        }

        Person person = sessionHelper.getPerson();

        // Check if person has authenticated with their ad password. If they have, replicate the AD password to the nsis password field
        if (sessionHelper.isAuthenticatedWithADPassword() && person.hasNSISUser()) {
            Person adPerson = sessionHelper.getADPerson();
            if (adPerson != null && Objects.equals(adPerson.getId(), person.getId())) {
                if (!StringUtils.isEmpty(person.getAdPassword())) {
                    person.setNsisPassword(person.getAdPassword());
                    personService.save(person);

                    sessionHelper.setAuthenticatedWithADPassword(false);
                }
            }
        }

        // Set authentication levels
        sessionHelper.setPerson(person);
        sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
        sessionHelper.setMFALevel(NSISLevel.SUBSTANTIAL);

        // Check if person is trying to login to SelfService, since it bypasses checks for locked and confirmed conditions
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(sessionHelper.getAuthnRequest());
        if (serviceProvider instanceof SelfServiceServiceProvider) {
            return initiateFlowOrCreateAssertion(model, response, request, person);
        }

        // Check if locked
        if (person.isLocked()) {
            return new ModelAndView("error-locked-account");
        }

        // Check confirmed conditions
        if (!person.isApprovedConditions()) {
            return new ModelAndView("error-conditions-not-approved");
        }

        return initiateFlowOrCreateAssertion(model, response, request, person);
    }
}