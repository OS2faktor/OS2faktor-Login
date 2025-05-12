package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PersonAttribute;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SqlServiceProviderAdvancedClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderGroupClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.dao.model.enums.RoleCatalogueOperation;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.AdvancedRuleService;
import dk.digitalidentity.common.service.EvaluationException;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.KnownNetworkService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonAttributeService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.PrivacyPolicyService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueService;
import dk.digitalidentity.controller.dto.ClaimValueDTO;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.service.model.enums.PasswordExpireStatus;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.service.serviceprovider.NemLoginServiceProvider;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.service.serviceprovider.SqlServiceProvider;
import dk.digitalidentity.util.IPUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FlowService {

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private MFAService mfaService;

	@Autowired
	private AssertionService assertionService;

	@Autowired
	private OidcAuthorizationCodeService oidcAuthorizationCodeService;

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
	private PersonAttributeService personAttributeService;

	@Autowired
	private LoginService loginService;

	@Autowired
	private PersonService personService;

	@Autowired
	private PasswordService passwordService;

	@Autowired
	private WSFederationService wsFederationService;

	@Autowired
	private KnownNetworkService knownNetworkService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private EntraMfaService entraMfaService;

	@Autowired
	private AdvancedRuleService advancedRuleService;

	@Autowired
	private RoleCatalogueService roleCatalogueService;
	
	public ModelAndView initiateFlowOrSendLoginResponse(Model model, HttpServletResponse response, HttpServletRequest request, Person person) throws ResponderException, RequesterException {
		ResponderException cannotPerformPassiveLogin = new ResponderException("Passiv login krævet, men bruger er ikke logget ind på det krævede niveau");

		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			String redirectUrl = sessionHelper.getPasswordChangeSuccessRedirect();
			sessionHelper.clearFlowStates();

			// we may end up here in special cases after finishing certain change-password subflows, and if we have a redirect URL,
			// we should attempt to use that to finish the flow, otherwise send them to the front-page of the IdP
			if (redirectUrl != null) {
				model.addAttribute("redirectUrl", redirectUrl);

				return new ModelAndView("changePassword/change-password-success", model.asMap());
			}

			// frontpage of IdP
			return new ModelAndView("redirect:/");
		}
		
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);

		// Should be caught earlier, but this is an extra check so we won't send any assertions for a person who is locked UNLESS specific requirements are met
		if (person.isLocked()) {
			// The only way to avoid being showed a locked error here is meeting all conditions:
			// * Only being locked by yourself
			// * Trying to log on to SelfService
			// * Having used MitID
			if (!person.isOnlyLockedByPerson() || !(serviceProvider instanceof SelfServiceServiceProvider) || !sessionHelper.isInNemIdOrMitIDAuthenticationFlow()) {
				sessionHelper.clearSession();
				return new ModelAndView("error-locked-account");
			}
		}

		NSISLevel currentNSISLevel = (sessionHelper.isInEntraMfaFlow())
				? sessionHelper.getMFALevel(serviceProvider, loginRequest)
				: sessionHelper.getLoginState(serviceProvider, loginRequest);

		NSISLevel requiredNSISLevel = serviceProvider.nsisLevelRequired(loginRequest);

		if (requiredNSISLevel == NSISLevel.HIGH) {
			throw new ResponderException("Understøtter ikke NSIS Høj");
		}

		boolean valid = sessionHelper.handleValidateIP();
		if (!valid || currentNSISLevel == null) {
			if (loginRequest.isPassive()) {
				throw cannotPerformPassiveLogin;
			}

			// Shortcut to NemID login. no reason to go through password login below if NemID is going to be required later in the flow
			if (requireNemId(loginRequest)) {
				return initiateNemIDOnlyLogin(model, request, null);
			}

			if (sessionHelper.isInPasswordlessMfaFlow()) {
				return initiateMFA(model, person, requiredNSISLevel);
			}

			return loginService.initiateLogin(model, request, serviceProvider.preferNemId());
		}

		// At this point the user is actually logged in, so we can start validation against the session

		// Should be caught earlier, but this is an extra check so we never approve a user that is not allowed access to a specific service
		RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
		if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
			auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
			throw new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
		}

		// Has the user approved conditions?
		if (personService.requireApproveConditions(person)) {
			if (loginRequest.isPassive()) {
				throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
			}
			else {
				return initiateApproveConditions(model);
			}
		}

		// Has the user activated their NSIS User?
		boolean declineUserActivation = sessionHelper.isDeclineUserActivation();
		if (!declineUserActivation && person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
			if (!loginRequest.isPassive()) {
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
						if (loginRequest.isPassive()) {
							throw cannotPerformPassiveLogin;
						}
						return selectClaimsPage;
					}

					return createAndSendLoginResponse(response, person, serviceProvider, loginRequest, model);
				case LOW:
					// if the service provider requires SUBSTANTIAL, then perform a step-up
					if (NSISLevel.SUBSTANTIAL.equalOrLesser(requiredNSISLevel)) {
						if (loginRequest.isPassive()) {
							throw cannotPerformPassiveLogin;
						}

						if (requireNemId(loginRequest)) {
							return initiateNemIDOnlyLogin(model, request, null);
						}

						if (!NSISLevel.SUBSTANTIAL.equalOrLesser(person.getNsisLevel())) {
							throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der kræver et NSIS sikringsniveau på Lav");
						}

						return initiateMFA(model, person, NSISLevel.SUBSTANTIAL);
					}

					selectClaimsPage = initiateSelectClaims(person, model, serviceProvider);
					if (selectClaimsPage != null) {
						if (loginRequest.isPassive()) {
							throw cannotPerformPassiveLogin;
						}
						return selectClaimsPage;
					}

					return createAndSendLoginResponse(response, person, serviceProvider, loginRequest, model);
				case NONE:
					if (loginRequest.isPassive()) {
						throw cannotPerformPassiveLogin;
					}

					if (!NSISLevel.LOW.equalOrLesser(person.getNsisLevel())) {
						throw new ResponderException("Brugerens sikringsniveau er for lavt og brugeren kan derfor kun logge ind på tjenesteudbydere, der ikke kræver et NSIS sikringsniveau");
					}

					RequireNemIdReason reason = requireNemId(loginRequest) ? null : RequireNemIdReason.AD;
					return initiateNemIDOnlyLogin(model, request, reason);
				default:
					// catch-all, should not really happen though, due to previous validations
					sessionHelper.clearSession();
					if (requireNemId(loginRequest)) {
						return initiateNemIDOnlyLogin(model, request, null);
					}

					return loginService.initiateLogin(model, request, serviceProvider.preferNemId());
			}
		}
		
		// in this case, the service provider does not require NSIS
		if (serviceProvider.mfaRequired(loginRequest, person.getDomain(), IPUtil.isIpInTrustedNetwork(knownNetworkService.getAllIPs(), request)) && !NSISLevel.NONE.equalOrLesser(sessionHelper.getMFALevel())) {
			if (loginRequest.isPassive()) {
				throw cannotPerformPassiveLogin;
			}

			return initiateMFA(model, person, NSISLevel.NONE);
		}
		
		// check if the persons password needs to be changed, note that this includes checking if the person has forceChangePassword = true
		// also note that the last argument is true (skip expires-soon-check), as that is not relevant when we are about to issue a loginResponse
		ModelAndView passwordExpiredPrompt = initiatePasswordExpired(person, model, true);
		if (passwordExpiredPrompt != null) {
			sessionHelper.setInPasswordExpiryFlow(true);
			return passwordExpiredPrompt;
		}

		ModelAndView selectClaimsPage = initiateSelectClaims(person, model, serviceProvider);
		if (selectClaimsPage != null) {
			return selectClaimsPage;
		}
		
		// user is already logged in at the required level
		return createAndSendLoginResponse(response, person, serviceProvider, loginRequest, model);
	}

	public ModelAndView initiateUserSelect(Model model, List<Person> people, NSISLevel authenticationLevel, LoginRequest loginRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws RequesterException, ResponderException {

		// if the person is in a dedicated activation flow, the list of available people will be
		// filtered to only match the people that CAN be activated
		if (sessionHelper.isInDedicatedActivateAccountFlow()) {
			people = people.stream()
					.filter(p -> !p.hasActivatedNSISUser() && p.isNsisAllowed())
					.collect(Collectors.toList());

			if (people.isEmpty()) {
				sessionHelper.clearSession();
				return new ModelAndView("activateAccount/no-account-to-activate-error");
			}
		}

		if (people.isEmpty()) {
			sessionHelper.clearSession();
			return new ModelAndView("error-no-people-to-select-from");
		}

		// show select-user page with the remaining "people" to choose from

		// filter duplicates
		HashSet<Long> seen = new HashSet<>();
		people.removeIf(e -> !seen.add(e.getId()));
		
		sessionHelper.setAvailablePeople(people);
		model.addAttribute("people", people);

		return new ModelAndView("select-user", model.asMap());
	}

	public ModelAndView initiateNemIDOnlyLogin(Model model, HttpServletRequest httpServletRequest, RequireNemIdReason reason) {
		return initiateNemIDOnlyLogin(model, httpServletRequest, reason, "");
	}

	public ModelAndView initiateNemIDOnlyLogin(Model model, HttpServletRequest httpServletRequest, RequireNemIdReason reason, String errCode) {
		model.addAttribute("reason", reason);
		model.addAttribute("errCode", errCode);
		model.addAttribute("waitForUserInputBckInfo", Objects.equals(reason, RequireNemIdReason.AD));

		// figure out if the user is in the middle of a NemLog-in loginflow
		boolean inNemLoginLogin = false;
        try {
            LoginRequest loginRequest = sessionHelper.getLoginRequest();
            if (loginRequest != null) {
                ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);
                if (serviceProvider != null && Objects.equals(NemLoginServiceProvider.SP_NAME, serviceProvider.getName(null))) {
                	inNemLoginLogin = true;
                }
            }
        }
        catch (Exception ignored) {
        	;
        }
        
        // if the user is in such a flow, clear the loginflow, it will not work, as we need to start
        // a new loginflow against NemLog-in to reset the password :)
		if (inNemLoginLogin) {
			try {
				sessionHelper.setLoginRequest(null);
			}
			catch (Exception ignored) {
				;
			}
		}
		
		if (commonConfiguration.getMitIdErhverv().isEnabled()) {
			sessionHelper.setRequestProfessionalProfile();
		}

		return new ModelAndView("login-nemid-only", model.asMap());
	}

	public ModelAndView initiateMFA(Model model, Person person, NSISLevel requiredNSISLevel) {
		List<MfaClient> clients = mfaService.getClients(person.getCpr(), person.isRobot());
		if (clients == null) {
			return new ModelAndView("error-could-not-get-mfa-devices");
		}

		boolean passwordless = sessionHelper.isInPasswordlessMfaFlow();
		
		clients = clients.stream()
				.filter(client -> requiredNSISLevel.equalOrLesser(client.getNsisLevel()) && (!passwordless || client.isPasswordless()))
				.collect(Collectors.toList());

		if (clients.size() == 0) {
			return new ModelAndView("error-no-mfa-devices");
		}

		sessionHelper.setMFAClients(clients);
		sessionHelper.setMFAClientRequiredNSISLevel(requiredNSISLevel);

		if (clients.size() == 1) {
			String deviceId = clients.get(0).getDeviceId();

			if (clients.get(0).isLocked()) {
				clients.sort(Comparator.comparing(MfaClient::getName));
				movePrimeClientToTop(clients);
				model.addAttribute("clients", clients);

				return new ModelAndView("login-mfa", model.asMap());
			}
			
			return new ModelAndView("redirect:/sso/saml/mfa/" + deviceId, model.asMap());
		}
		else {
			clients.sort(Comparator.comparing(MfaClient::getName));
			movePrimeClientToTop(clients);
			model.addAttribute("clients", clients);

			return new ModelAndView("login-mfa", model.asMap());
		}
	}

	public ModelAndView initiateApproveConditions(Model model) {
		sessionHelper.setInApproveConditionsFlow(true);

		model.addAttribute("terms", termsAndConditionsService.getTermsAndConditions());
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

	public ModelAndView initiatePasswordExpired(Person person, Model model, boolean skipIfNotRequired) throws ResponderException {
		PasswordExpireStatus passwordStatus = passwordService.getPasswordExpireStatus(person);
		
		switch (passwordStatus) {
			case OK:
				return null;
			case FORCE_CHANGE:
			case EXPIRED:
				sessionHelper.setInPasswordExpiryFlow(true);
				model.addAttribute("forced", true);
				model.addAttribute("daysLeft", 0);

				return new ModelAndView("password-expiry-prompt", model.asMap());
			case ALMOST_EXPIRED:
				if (skipIfNotRequired) {
					return null;
				}

				PasswordSetting settings = passwordSettingService.getSettings(person);
				LocalDateTime expiredTimestamp = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());

				long daysLeftPassword = (person.getPasswordTimestamp() != null) ? ChronoUnit.DAYS.between(expiredTimestamp, person.getPasswordTimestamp()) : Long.MAX_VALUE;
				long daysLeft = (daysLeftPassword != Long.MAX_VALUE && daysLeftPassword > 0) ? daysLeftPassword : 0;
				model.addAttribute("daysLeft", daysLeft);

				model.addAttribute("forced", false);
				sessionHelper.setInPasswordExpiryFlow(true);

				return new ModelAndView("password-expiry-prompt", model.asMap());
			case NO_PASSWORD:
				return initiateActivateNSISAccount(model);
		}
		
		log.error("Person in unexpected password state " + person.getId() + " : " + passwordStatus);
		
		throw new ResponderException("Brugeren var i en uventet tilstand");
	}

	public ModelAndView initiateSelectClaims(Person person, Model model, ServiceProvider serviceProvider) throws ResponderException {
		if (sessionHelper.isInSelectClaimsFlow()) {
			return null;
		}

		if (person == null) {
			return null;
		}

		if (serviceProvider == null) {
			return null;
		}

		if (!(serviceProvider instanceof SqlServiceProvider)) {
			return null;
		}

		SqlServiceProvider sqlSP = (SqlServiceProvider) serviceProvider;

		// Determine the list of claims that the person has where they have multiple values associated with a key delimited by ;
		// and where the SP requires us to only return a single value
		Map<String, String> personAttributes = person.getAttributes();
		Map<String, ClaimValueDTO> toBeDecided = new HashMap<>();

		if (person.getAttributes() != null && !person.getAttributes().isEmpty()) {
			sqlSP.getRequiredFields().stream().filter(SqlServiceProviderRequiredField::isSingleValueOnly).filter(requiredField -> personAttributes.containsKey(requiredField.getPersonField()) && personAttributes.get(requiredField.getPersonField()).contains(";")).forEach(requiredField -> {
				PersonAttribute personAttribute = personAttributeService.getByName(requiredField.getPersonField());
				String displayName = requiredField.getAttributeName();
				if (personAttribute != null && StringUtils.hasLength(personAttribute.getDisplayName())) {
					displayName = personAttribute.getDisplayName();
				}
	
				toBeDecided.put(requiredField.getAttributeName(), new ClaimValueDTO(displayName, new ArrayList<>(Arrays.asList(personAttributes.get(requiredField.getPersonField()).split(";")))));
			});
		}
		
		handleSingleValueOnlyAdvancedClaims(person, sqlSP, toBeDecided);
		
		handleSingleValueOnlyGroupClaims(person, sqlSP, toBeDecided);
		
		handleSingleValueOnlyRoleCatalogClaims(person, sqlSP, toBeDecided);
		
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

	private void handleSingleValueOnlyAdvancedClaims(Person person, SqlServiceProvider sqlSP, Map<String, ClaimValueDTO> toBeDecided) throws ResponderException {
		//Find duplicate claims
		HashSet<String> seenClaimNames = new HashSet<>();
		HashSet<String> duplicateClaimNames = new HashSet<>();
		Map<String, HashSet<SqlServiceProviderAdvancedClaim>> duplicateAdvancedClaims = new HashMap<>();

		for (SqlServiceProviderAdvancedClaim claim : sqlSP.getAdvancedClaims().stream()
				.sorted((o1, o2) -> o1.getClaimName().compareToIgnoreCase(o2.getClaimName()))
				.collect(Collectors.toList())) {

			if (!seenClaimNames.add(claim.getClaimName())) {
				duplicateClaimNames.add(claim.getClaimName());
			}
		}
		
		// Generate a map of name and claims <String, Set<AdvancedClaim>>
		for (SqlServiceProviderAdvancedClaim claim : sqlSP.getAdvancedClaims().stream()
				.sorted((o1, o2) -> o1.getClaimName().compareToIgnoreCase(o2.getClaimName()))
				.collect(Collectors.toList())) {

			if (duplicateClaimNames.contains(claim.getClaimName())) {
				if (duplicateAdvancedClaims.containsKey(claim.getClaimName())) {
					HashSet<SqlServiceProviderAdvancedClaim> duplicates = duplicateAdvancedClaims.get(claim.getClaimName());
					
					if (duplicates == null || duplicates.isEmpty()) {
						duplicateAdvancedClaims.put(claim.getClaimName(), new HashSet<>(Set.of(claim)));
					}
					else {
						duplicates.add(claim);
						duplicateAdvancedClaims.put(claim.getClaimName(), duplicates);
					}
				}
				else {
					duplicateAdvancedClaims.put(claim.getClaimName(), new HashSet<>(Set.of(claim)));
				}
			}
		}
		
		for (String key : duplicateAdvancedClaims.keySet()) {
			// Check if any of the claims is marked as singleValueOnly
			if (duplicateAdvancedClaims.get(key).stream().anyMatch(claim -> claim.isSingleValueOnly())) {
				List<String> values = new ArrayList<>();

				for (SqlServiceProviderAdvancedClaim claim : duplicateAdvancedClaims.get(key)) {
					try {
						String value = advancedRuleService.evaluateRule(claim.getClaimValue(), person);
						// claims with multiple values will be handled later
						if (value.contains(";")) {
							continue;
						}

						values.add(value);
					}
					catch (EvaluationException e) {
						throw new ResponderException("Fejl opstået under evaluering af et avanceret claim: " + e.getMessage(), e);
					}
				}

				// add new claim to select
				toBeDecided.put(key, new ClaimValueDTO(key, values));
			}
		}

		// handle claims with multiple values
		try {
			for (SqlServiceProviderAdvancedClaim advClaim : sqlSP.getAdvancedClaims().stream()
					.sorted((o1, o2) -> o1.getClaimName().compareToIgnoreCase(o2.getClaimName()))
					.collect(Collectors.toList())) {

				if (!advClaim.isSingleValueOnly()) {
					// skipping claim not a single value
					continue;
				}
				
				String claimValueEvaluated = advancedRuleService.evaluateRule(advClaim.getClaimValue(), person);
				
				if (claimValueEvaluated.contains(";")) {
					// found claim with multiple values
					if (toBeDecided.containsKey(advClaim.getClaimName())) {
						toBeDecided.get(advClaim.getClaimName()).getAcceptedValues().addAll(new ArrayList<>(
							Arrays.asList(advancedRuleService.evaluateRule(advClaim.getClaimValue(), person).split(";")))
						);
					}
					else {
						toBeDecided.put(advClaim.getClaimName(), new ClaimValueDTO(advClaim.getClaimName(), new ArrayList<>(
							Arrays.asList(advancedRuleService.evaluateRule(advClaim.getClaimValue(), person).split(";"))))
						);
					}
				}
			}
		}
		catch (EvaluationException e) {
			throw new ResponderException("Det var ikke muligt at danne et avanceret claim: " + e.getMessage(), e);
		}

	}

	private void handleSingleValueOnlyGroupClaims(Person person, SqlServiceProvider sqlSP, Map<String, ClaimValueDTO> toBeDecided) {
		//filter by person's groups
		List<SqlServiceProviderGroupClaim> groupClaims = sqlSP.getGroupClaims().stream()
				.filter(c -> GroupService.memberOfGroup(person, c.getGroup()))
				.sorted((o1, o2) -> o1.getClaimName().compareToIgnoreCase(o2.getClaimName()))
				.collect(Collectors.toList());
		
		//Find duplicate claims
		HashSet<String> seenClaimNames = new HashSet<>();
		HashSet<String> duplicateClaimNames = new HashSet<>();
		Map<String, HashSet<SqlServiceProviderGroupClaim>> duplicateGroupClaims = new HashMap<>();
		
		for (SqlServiceProviderGroupClaim claim : groupClaims) {
			if (!seenClaimNames.add(claim.getClaimName())) {
				duplicateClaimNames.add(claim.getClaimName());
			}
		}
		
		// Generate a map of name and claims <String, Set<GroupClaim>>
		for (SqlServiceProviderGroupClaim claim : groupClaims) {
			if (duplicateClaimNames.contains(claim.getClaimName())) {
				if (duplicateGroupClaims.containsKey(claim.getClaimName())) {
					HashSet<SqlServiceProviderGroupClaim> duplicates = duplicateGroupClaims.get(claim.getClaimName());
					
					if (duplicates == null || duplicates.isEmpty()) {
						duplicateGroupClaims.put(claim.getClaimName(), new HashSet<>(Set.of(claim)));
					}
					else {
						duplicates.add(claim);
						duplicateGroupClaims.put(claim.getClaimName(), duplicates);
					}
				}
				else {
					duplicateGroupClaims.put(claim.getClaimName(), new HashSet<>(Set.of(claim)));
				}
			}
		}
		
		for (String key : duplicateGroupClaims.keySet()) {
			// Check if any of the claims is marked as singleValueOnly
			if (duplicateGroupClaims.get(key).stream().anyMatch(claim -> claim.isSingleValueOnly())) {
				List<String> values = new ArrayList<>();
				for (SqlServiceProviderGroupClaim claim : duplicateGroupClaims.get(key)) {
					values.add(claim.getClaimValue());
				}
				
				toBeDecided.put(key, new ClaimValueDTO(key, values));
			}
		}
	}

	private void handleSingleValueOnlyRoleCatalogClaims(Person person, SqlServiceProvider sqlSP, Map<String, ClaimValueDTO> toBeDecided) {
		List<SqlServiceProviderRoleCatalogueClaim> rcClaims = sqlSP.getRcClaims().stream()
				.sorted((o1, o2) -> o1.getClaimName().compareToIgnoreCase(o2.getClaimName()))
				.collect(Collectors.toList());
		
		rcClaims.stream().filter(SqlServiceProviderRoleCatalogueClaim::isSingleValueOnly).forEach(claim -> {
			RoleCatalogueOperation externalOperation = claim.getExternalOperation();
			Map<String, String> acceptedValuesWithNames = null;

			if (externalOperation == RoleCatalogueOperation.GET_USER_ROLES) {
				acceptedValuesWithNames = roleCatalogueService.getUserRolesWithDisplayNames(person, claim.getExternalOperationArgument());
			}
			else if (externalOperation == RoleCatalogueOperation.GET_SYSTEM_ROLES) {
				acceptedValuesWithNames = roleCatalogueService.getSystemRolesDisplayName(person, claim.getExternalOperationArgument());
			}

			if (acceptedValuesWithNames.size() > 1) {
				toBeDecided.put(claim.getClaimName(), new ClaimValueDTO(claim.getClaimName(), acceptedValuesWithNames));
			}
		});
	}

	private ModelAndView createAndSendLoginResponse(HttpServletResponse httpServletResponse, Person person, ServiceProvider serviceProvider, LoginRequest loginRequest, Model model) throws ResponderException, RequesterException {
		switch (serviceProvider.getProtocol()) {
			case SAML20:
				assertionService.createAndSendAssertion(httpServletResponse, person, serviceProvider, loginRequest);
				break;
			case OIDC10:
				// TODO handle scope consent?
				oidcAuthorizationCodeService.createAndSendAuthorizationCode(httpServletResponse, person, serviceProvider, loginRequest);
				break;
			case WSFED:
				return wsFederationService.createAndSendSecurityTokenResponse(model, person, serviceProvider, loginRequest);
			case ENTRAMFA:
				return entraMfaService.createAndSendIdToken(httpServletResponse, person, serviceProvider, loginRequest);
		}

		return null;
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

		String sAMAccountName = null;
		if (person.getSamaccountName() != null && !person.getDomain().isStandalone()) {
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

		PasswordSetting settings = passwordSettingService.getSettings(person);
		String samaccountName = null;

		if (person.getSamaccountName() != null && !person.getDomain().isStandalone()) {
			samaccountName = person.getSamaccountName();
		}

		model.addAttribute("authenticatedWithNemId", sessionHelper.isAuthenticatedWithNemIdOrMitId());
		model.addAttribute("samaccountName", samaccountName);
		model.addAttribute("settings", settings);
		model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(person));
		model.addAttribute("passwordForm", (form != null) ? form : new PasswordChangeForm());

		SchoolClass schoolClass = personService.isYoungStudent(person);
		model.addAttribute("youngStudent", (schoolClass != null));

		if (schoolClass != null) {
			List<String> firstWordList = schoolClass.getPasswordWords().stream().map(w -> w.getWord()).map(w -> StringUtils.capitalize(w)).collect(Collectors.toList());
			Collections.shuffle(firstWordList);

			model.addAttribute("firstWordList", firstWordList);
			List<String> numberList = getNumberList();

			Collections.shuffle(numberList);
			model.addAttribute("numberList", numberList);

			List<String> secondWordList = schoolClass.getPasswordWords().stream().map(w -> w.getWord()).collect(Collectors.toList());
			Collections.shuffle(secondWordList);
			model.addAttribute("secondWordList", secondWordList);
		}
        
		return new ModelAndView("changePassword/change-password", model.asMap());
	}

	private List<String> getNumberList() {
		List<String> numbers = new ArrayList<>();
		int[] digits = IntStream.range(0, 10).toArray();
		
		for (int digit : digits) {
			for (int digit2 : digits) {
				numbers.add("" + digit + digit2);
			}
		}
		
		return numbers;
	}

	private boolean requireNemId(LoginRequest loginRequest) {
		if (loginRequest == null) {
			return false;
		}

		switch (loginRequest.getProtocol()) {
			case SAML20:
				AuthnRequest authnRequest = loginRequest.getAuthnRequest();
				return authnRequestService.requireNemId(authnRequest);
			case OIDC10:
				return false;
			case WSFED:
				return false; // TODO maybe we need to support WSFed requests that indicate they want NemId in the future.
			case ENTRAMFA:
				return false;
		}
		
		return false;
	}

	private void movePrimeClientToTop(List<MfaClient> clients) {
		if (clients == null || clients.size() == 1) {
			return;
		}
		
		MfaClient primeClient = clients.stream().filter(c -> c.isPrime()).findAny().orElse(null);
		
		if (primeClient == null) {
			return;
		}
		
		clients.add(0, clients.remove(clients.indexOf(primeClient)));
	}
}
