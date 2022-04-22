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
		all.add(new CmsMessageListDTO("cms.changePassword.content", getDescription("cms.changePassword.content")));
		all.add(new CmsMessageListDTO("cms.changePassword.identification", getDescription("cms.changePassword.identification")));
		all.add(new CmsMessageListDTO("cms.login.mfaChallenge.content.top", getDescription("cms.login.mfaChallenge.content.top")));
		all.add(new CmsMessageListDTO("cms.myidentity.mydata.help", getDescription("cms.myidentity.mydata.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.mfaclients.help", getDescription("cms.myidentity.mfaclients.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.logs.help", getDescription("cms.myidentity.logs.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.actions.help", getDescription("cms.myidentity.actions.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.links.help", getDescription("cms.myidentity.links.help")));
		all.add(new CmsMessageListDTO("cms.myidentity.references.help", getDescription("cms.myidentity.references.help")));
		all.add(new CmsMessageListDTO("cms.index.content", getDescription("cms.index.content")));
		all.add(new CmsMessageListDTO("cms.activate.initiate", getDescription("cms.activate.initiate")));
		all.add(new CmsMessageListDTO("cms.activate.ad-password-change", getDescription("cms.activate.ad-password-change")));
		all.add(new CmsMessageListDTO("cms.activate.during-login", getDescription("cms.activate.during-login")));
		all.add(new CmsMessageListDTO("cms.activate.dedicated", getDescription("cms.activate.dedicated")));
		all.add(new CmsMessageListDTO("cms.login.nemlogin.description", getDescription("cms.login.nemlogin.description")));
		all.add(new CmsMessageListDTO("cms.activate.personDead", getDescription("cms.activate.personDead")));
		all.add(new CmsMessageListDTO("cms.changePassword.canNotChangePasswordGroup.content", getDescription("cms.changePassword.canNotChangePasswordGroup.content")));

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
				return "Den ledetekst der vises på siden, man lander på, hvis man ikke har en 2-faktor enhed";

			case "cms.changePassword.identification":
				return "Den forklaring der vises ved siden af NemID login skærmbilledet når man skal bruge NemID i forbindelse med genskabelse af glemt kodeord";
			case "cms.changePassword.content":
				return "Den ledetekst der vises øverst i boksen på siden hvor man skifter kodeord";
			case "cms.changePassword.canNotChangePasswordGroup.content":
				return "Den fejlbesked der vises, når en person, der er i en gruppe, der ikke må skifte kodeord, forsøger at skifte kodeord";

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

			case "cms.index.content":
				return "Den ledetekst der vises på forsiden af selvbetjeningen før man er logget ind";

			case "cms.activate.initiate":
				return "Den ledetekst der vises på startsiden for det dedikerede aktiveringsflow";
			case "cms.activate.ad-password-change":
				return "Den forklaring der vises ved siden af NemID login skærmbilledet når man skal bruge NemID i forbindelse med et kodeordsskifte som er foretaget udenfor løsningen (gen-indrullering af kodeord)";
			case "cms.activate.during-login":
				return "Den forklaring der vises ved siden af NemID login skærmbilledet når man skal bruge NemID i forbindelse med identifikationen ved det indlejrede aktiveringsflow (i forbindelse med et normalt login)";
			case "cms.activate.dedicated":
				return "Den forklaring der vises ved siden af NemID login skærmbilledet når man skal bruge NemID i forbindelse med identifikationen ved det dedikerede aktiveringsflow";
			case "cms.activate.personDead":
				return "Den fejlbesked der vises, hvis cpr opslaget under aktivering viser at personen er død";

			default:
				log.error("Key does not have a description: " + key);
				return "";
		}
	}
}
