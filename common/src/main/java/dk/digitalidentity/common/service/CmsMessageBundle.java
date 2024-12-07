package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.CmsMessage;
import dk.digitalidentity.common.service.dto.CmsMessageListDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CmsMessageBundle {

	@Autowired
	private CmsMessageService cmsMessageService;
	
	@Autowired
	private CmsMessageSource messageSource;
	
	public String getText(String key) {
		return getText(key, false);
	}
	
	public String getText(String key, boolean bypassCache) {
		String value = null;

		if (bypassCache) {
			CmsMessage cmsMessage = cmsMessageService.getByCmsKey(key);
			if (cmsMessage != null) {
				value = cmsMessage.getCmsValue();
			}
		}
		else {
			value = cmsMessageService.getCmsMap().get(key);
		}

		if (value == null) {
			// This accesses both the messages.properties in the ui and idp project depending on where the
			// method is called from. Means that the texts are in both
			value = messageSource.getMessage(key, null, "", null);
		}

		return value;
	}
	
	public List<CmsMessageListDTO> getAll() {
		List<CmsMessageListDTO> all = new ArrayList<>();

		all.add(new CmsMessageListDTO("cms.login.help", getDescription("cms.login.help")));
		all.add(new CmsMessageListDTO("cms.login.mfa.help", getDescription("cms.login.mfa.help")));
		all.add(new CmsMessageListDTO("cms.login.mfa.content", getDescription("cms.login.mfa.content")));
		all.add(new CmsMessageListDTO("cms.login.selectUser.help", getDescription("cms.login.selectUser.help")));
		all.add(new CmsMessageListDTO("cms.login.selectUser.content", getDescription("cms.login.selectUser.content")));
		all.add(new CmsMessageListDTO("cms.login.content", getDescription("cms.login.content")));
		all.add(new CmsMessageListDTO("cms.login.mfa.noClients", getDescription("cms.login.mfa.noClients")));
		all.add(new CmsMessageListDTO("cms.login.nsisLevelTooLow", getDescription("cms.login.nsisLevelTooLow")));
		all.add(new CmsMessageListDTO("cms.changePassword.content", getDescription("cms.changePassword.content")));
		all.add(new CmsMessageListDTO("cms.changePasswordParent.content", getDescription("cms.changePasswordParent.content")));
		all.add(new CmsMessageListDTO("cms.changePassword.identification", getDescription("cms.changePassword.identification")));
		all.add(new CmsMessageListDTO("cms.login.mfaChallenge.content.top", getDescription("cms.login.mfaChallenge.content.top")));
		all.add(new CmsMessageListDTO("cms.myidentity.mydata.help", getDescription("cms.myidentity.mydata.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.mfaclients.help", getDescription("cms.myidentity.mfaclients.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.logs.help", getDescription("cms.myidentity.logs.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.actions.help", getDescription("cms.myidentity.actions.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.links.help", getDescription("cms.myidentity.links.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.references.help", getDescription("cms.myidentity.references.help")));
		all.add(new CmsMessageListDTO("cms.index.content", getDescription("cms.index.content")));
		all.add(new CmsMessageListDTO("cms.index.forgotPasswordOrLocked", getDescription("cms.index.forgotPasswordOrLocked")));
		all.add(new CmsMessageListDTO("cms.activate.initiate", getDescription("cms.activate.initiate")));
		all.add(new CmsMessageListDTO("cms.activate.ad-password-change", getDescription("cms.activate.ad-password-change")));
		all.add(new CmsMessageListDTO("cms.activate.during-login", getDescription("cms.activate.during-login")));
		all.add(new CmsMessageListDTO("cms.activate.dedicated", getDescription("cms.activate.dedicated")));
		all.add(new CmsMessageListDTO("cms.login.nemlogin.description", getDescription("cms.login.nemlogin.description")));
		all.add(new CmsMessageListDTO("cms.activate.personDead", getDescription("cms.activate.personDead")));
		all.add(new CmsMessageListDTO("cms.changePassword.canNotChangePasswordGroup.content", getDescription("cms.changePassword.canNotChangePasswordGroup.content")));
		all.add(new CmsMessageListDTO("cms.lockAccount.content", getDescription("cms.lockAccount.content")));
		all.add(new CmsMessageListDTO("cms.links.description", getDescription("cms.links.description")));
		all.add(new CmsMessageListDTO("cms.forgotPasswordOrLocked.changePassword", getDescription("cms.forgotPasswordOrLocked.changePassword")));
		all.add(new CmsMessageListDTO("cms.forgotPasswordOrLocked.unlockAD", getDescription("cms.forgotPasswordOrLocked.unlockAD")));
		all.add(new CmsMessageListDTO("cms.password.mismatch.content", getDescription("cms.password.mismatch.content")));
		all.add(new CmsMessageListDTO("cms.password.complexity.warning", getDescription("cms.password.complexity.warning")));
		all.add(new CmsMessageListDTO("cms.password.complexity.final", getDescription("cms.password.complexity.final")));
		all.add(new CmsMessageListDTO("cms.password.leak.warning", getDescription("cms.password.leak.warning")));
		all.add(new CmsMessageListDTO("cms.password.leak.final", getDescription("cms.password.leak.final")));
		all.add(new CmsMessageListDTO("cms.unlockAccount.content", getDescription("cms.unlockAccount.content")));
		all.add(new CmsMessageListDTO("cms.account.expired", getDescription("cms.account.expired")));
		all.add(new CmsMessageListDTO("cms.login.selectClaims.content.top", getDescription("cms.login.selectClaims.content.top")));
		all.add(new CmsMessageListDTO("cms.login.error.locked-account", getDescription("cms.login.error.locked-account")));
		all.add(new CmsMessageListDTO("cms.account.no-account-to-activate", getDescription("cms.account.no-account-to-activate")));
		all.add(new CmsMessageListDTO("cms.changePassword.too-many-times.error", getDescription("cms.changePassword.too-many-times.error")));
		all.add(new CmsMessageListDTO("cms.changePassword.content.error", getDescription("cms.changePassword.content.error")));
		all.add(new CmsMessageListDTO("cms.technical-error", getDescription("cms.technical-error")));
		all.add(new CmsMessageListDTO("cms.myidentity.mfaclients.error", getDescription("cms.myidentity.mfaclients.error")));
		all.add(new CmsMessageListDTO("cms.account.no-account.error", getDescription("cms.account.no-account.error")));
		all.add(new CmsMessageListDTO("cms.account.locked.failed-attempts", getDescription("cms.account.locked.failed-attempts")));
		all.add(new CmsMessageListDTO("cms.login.selectUser.content.error", getDescription("cms.login.selectUser.content.error")));
		all.add(new CmsMessageListDTO("cms.activate-validate-ad-password.text", getDescription("cms.activate-validate-ad-password.text")));
		all.add(new CmsMessageListDTO("cms.activate-validate-ad-password.heading", getDescription("cms.activate-validate-ad-password.heading")));
		all.add(new CmsMessageListDTO("cms.self-service.add.2-factor.text",getDescription("cms.self-service.add.2-factor.text")));
		all.add(new CmsMessageListDTO("cms.self-service.add.2-factor.heading",getDescription("cms.self-service.add.2-factor.heading")));
		
		all.add(new CmsMessageListDTO("cms.login-error.nsis-not-allowed",getDescription("cms.login-error.nsis-not-allowed")));
		all.add(new CmsMessageListDTO("cms.unlockAccount.insufficient-permission", getDescription("cms.unlockAccount.insufficient-permission")));
		all.add(new CmsMessageListDTO("cms.changePassword.insufficient-permission", getDescription("cms.changePassword.insufficient-permission")));
		
		return all;
	}
	
	public String getDescription(String key) {
		switch(key) {
			case "cms.login.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet på login-siden";
			case "cms.login.mfa.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet på siden med listen af mulige 2-faktor enheder under login";
			case "cms.login.mfa.content":
				return "Den ledetekst der vises øverst i boksen på siden med listen af mulige 2-faktor enheder under login";
			case "cms.login.selectUser.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet på vælg-bruger siden under login";
			case "cms.login.selectUser.content":
				return "Den ledetekst der kan vises øverst i boksen på siden, hvor man skal vælge bruger i tilfælde af at man har flere brugere at vælge mellem under login";
			case "cms.login.content":
				return "Den ledetekst der kan vises øverst i boksen på siden hvor man logger ind";
			case "cms.login.mfaChallenge.content.top":
				return "Den ledetekst der vises øverst i boksen på siden, der vises efter man har valgt en 2-faktor enhed og der afventes brugergodkendelse";
			case "cms.login.nemlogin.description":
				return "Den ledetekst der vises på login-siden, når man vælger fanen 'MitID'";
			case "cms.login.mfa.noClients":
				return "Den ledetekst der vises på siden man lander på, hvis man ikke har en 2-faktor enhed";
			case "cms.login.selectUser.content.error":
				return "Den fejlbesked der vises, når der ikke er nogle tilgængelige brugerkonti. Det kan være fordi alle brugere er låst eller der ikke er nogen brugerkontier";
			case "cms.login.error.locked-account":
				return "Den fejlbesked der vises, når kontoen er låst";
			case "cms.login.nsisLevelTooLow":
				return "Den ledetekst der vises på siden man lander på, hvis det sikringsniveau (NSIS niveau) der lå til grund for login ikke var højt nok";
			case "cms.changePassword.identification":
				return "Den forklaring der vises ved siden af MitID login skærmbilledet når man skal bruge MitID i forbindelse med genskabelse af glemt kodeord";
			case "cms.changePassword.content":
				return "Den ledetekst der vises øverst i boksen på siden hvor man skifter kodeord";
			case "cms.changePasswordParent.content":
				return "Den ledetekst der vises øverst i boksen på siden hvor man skifter kodeord (for forældre til elever)";
			case "cms.changePassword.canNotChangePasswordGroup.content":
				return "Den fejlbesked der vises, når en person, der er i en gruppe, der ikke må skifte kodeord, forsøger at skifte kodeord";
			case "cms.changePassword.too-many-times.error":
				return "Den fejbesked der vises, når man har skiftet password for ofte på én dag.";
			case "cms.changePassword.content.error":
				return "Den fejlbesked der vises, når man forsøger at skifte til et ikke-tilladt password.";
			case "cms.myidentity.mydata.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet der findes øverst i 'Brugerkonto' boksen på 'Min identitet'-siden";
			case "cms.myidentity.mfaclients.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet der findes øverst i '2-faktor enheder' boksen på 'Min identitet'-siden";
			case "cms.myidentity.logs.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet der findes øverst i 'Hændelseslog' boksen på 'Min identitet'-siden";
			case "cms.myidentity.actions.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet der findes øverst i 'Handlinger' boksen på 'Min identitet'-siden";
			case "cms.myidentity.links.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet der findes øverst i 'Links' boksen på 'Min identitet'-siden";
			case "cms.myidentity.references.help":
				return "Den hjælpetekst der vises, når musen holdes over ?-tegnet der findes øverst i 'Referencer' boksen på 'Min identitet'-siden";
			case "cms.myidentity.mfaclients.error":
				return "Den fejlbesked der vises, når man ikke kan hente de registrerede 2-faktor enheder på en bruger";
			case "cms.index.content":
				return "Den ledetekst der vises på forsiden af selvbetjeningen før man er logget ind";
			case "cms.index.forgotPasswordOrLocked":
				return "Den tekst der står på knappen, man skal trykke på, hvis man har glemt kodeord eller er låst ude";
			case "cms.activate.initiate":
				return "Den ledetekst der vises på startsiden for det dedikerede aktiveringsflow";
			case "cms.activate.ad-password-change":
				return "Den forklaring der vises ved siden af MitID login skærmbilledet når man skal bruge MitID i forbindelse med et kodeordsskifte som er foretaget udenfor løsningen (gen-indrullering af kodeord)";
			case "cms.activate.during-login":
				return "Den forklaring der vises ved siden af MitID login skærmbilledet når man skal bruge MitID i forbindelse med identifikationen ved det indlejrede aktiveringsflow (i forbindelse med et normalt login)";
			case "cms.activate.dedicated":
				return "Den forklaring der vises ved siden af MitID login skærmbilledet når man skal bruge MitID i forbindelse med identifikationen ved det dedikerede aktiveringsflow";
			case "cms.activate.personDead":
				return "Den fejlbesked der vises, hvis cpr opslaget under aktivering viser at personen er død";
			case "cms.lockAccount.content":
				return "Den tekst der vises på siden, hvor en bruger kan låse sin erhvervsidentitet.";
			case "cms.unlockAccount.content":
				return "Den tekst der vises på siden, hvor en bruger kan fjerne låsen på sin erhvervsidentitet.";
			case "cms.technical-error":
				return "Den fejlbesked der vises, når der sker en teknisk fejl.";
			case "cms.links.description":
				return "Den tekst der vises under overskiften i boksen med links";
			case "cms.forgotPasswordOrLocked.changePassword":
				return "Den tekst der vises ud for skift kodeord knappen på siden hvor man vælger om man vil skifte kodeord eller låse sin AD konto op.";
			case "cms.forgotPasswordOrLocked.unlockAD":
				return "Den tekst der vises ud for lås AD konto op knappen på siden hvor man vælger om man vil skifte kodeord eller låse sin AD konto op.";
			case "cms.password.mismatch.content":
				return "Den tekst der vises for brugeren, hvis deres AD kodeord er kommet ud af sync med deres OS2faktor kodeord, når de forsøger at logge ind i selvbetjeningen";
			case "cms.account.expired":
				return "Den tekst der vises for brugeren, hvis deres konto er udløben, og de derfor ikke kan gennemføre et login";
			case "cms.account.no-account-to-activate":
				return "Den fejlbesked der vises, når man forsøger at aktivere en ugyldig- eller allerede aktiveret bruger.";
			case "cms.account.no-account.error":
				return "Den fejlbesked der vises, når en bruger ikke eksistere";
			case "cms.account.locked.failed-attempts":
				return "Den fejlbesked der vises, når man har forsøgt at logge ind for mange gange.";
			case "cms.login.selectClaims.content.top":
				return "Den tekst der vises for brugeren, hvis de er ved at logge på en tjenesteudbyder der kun kan modtage en værdi per claim og ikke en liste og derfor skal brugeren vælge hvilke claims de vil gøre brug af";
			case "cms.activate-validate-ad-password.text":
				return "Den brødtekst der vises, når man validere et eksisterende AD kodeord";
			case "cms.activate-validate-ad-password.heading":
				return "Overskriften for validering af eksisterende AD kodeord";
			case "cms.self-service.add.2-factor.text":
				return "Den vejledende tekst til tilknytning af 2 faktor-enhed";
			case "cms.self-service.add.2-factor.heading":
				return "Overskriften for tilknytning af 2 faktor-enhed på selvbetjeningssiden";
			case "cms.login-error.nsis-not-allowed":
				return "Hjælpetekst til brugeren når denne forsøger at logge på en tjeneste der kræver et NSIS sikringsniveau, men ikke er tildelt en erhvervsidentitet";			
			case "cms.changePassword.insufficient-permission":
				return "Teksten der vises hvis OS2faktor ikke har de fornødne rettigheder til at skifte kodeord på brugerens konto i AD";
			case "cms.unlockAccount.insufficient-permission":
				return "Teksten der vises hvis OS2faktor ikke har de fornødne rettigheder til at låse brugerens konto op i AD";
			case "cms.password.complexity.warning":
				return "Teksten der vises hvis en brugers nuværende kodeord ikke lever op til kravene om kodeordskompleksitet, og brugeren stadig har en periode til at nå at skifte kodeordet i";
			case "cms.password.complexity.final":
				return "Teksten der vises hvis en brugers nuværende kodeord ikke lever op til kravene om kodeordskompleksitet, og brugeren er tvunget til at skifte kodeordet vha MitID";
			case "cms.password.leak.warning":
				return "Teksten der vises hvis en brugers nuværende kodeord er fundet i lister med lækkede kodeord, og brugeren stadig har en periode til at nå at skifte kodeordet i";
			case "cms.password.leak.final":
				return "Teksten der vises hvis en brugers nuværende kodeord er fundet i lister med lækkede kodeord, og brugeren er tvunget til at skifte kodeordet vha MitID";
			default:
				log.error("Key does not have a description: " + key);
				return "";
		}
	}
}
