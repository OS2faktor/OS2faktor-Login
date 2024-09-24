package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.TUTermsAndConditionsDao;
import dk.digitalidentity.common.dao.model.TUTermsAndConditions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TUTermsAndConditionsService {

	private static final String fixedTerms = "<p>Disse vilkår regulerer tjenesteudbyderes anvendelse af kommunens logintjeneste, og de oplysninger der stilles til rådighed i forbindelse med login.</p>\n"
			+ "\n"
			+ "<h4>Anvendelse af data</h4>\n"
			+ "<p>Kommunens logintjeneste udsteder loginbilletter der indeholder oplysninger om kommunens brugere. Disse oplysninger skal behandles fortroligt, og må ikke videregives til 3.part uden kommunens explicitte tilladelse. Dette inkluderer viderestilling af information til andre tjenester i forbindelse med direkte integrationer eller anden udveksling af data.</p>\n"
			+ "\n"
			+ "<p>Tjenesteudbyderen skal sikre at data er beskyttet på et passende niveau under hele behandlingsperioden, og at både behandling og opbevaring af data overholder reglerne i databeskyttelsesforordningen.</p>\n"
			+ "\n"
			+ "<h4>Forpligtigelser</h4>\n"
			+ "<p>Tjenesteudbyderen er forpligtiget til at gøre kommunen opmærksom i relevante ændringer i teknisk opsætning, herunder skift af certifikat, krypteringsalgoritmer eller andre ændringer der kan påvirke integrationen til kommunens logintjeneste.</p>\n"
			+ "\n"
			+ "<p>Tjenesteudbyderen er ligeledes forpligtiget til, enten automatisk eller manuelt, at sikre opdatering af tillid til det certifikat der anvendes af kommunens logintjeneste. Til formålet udstiller kommunen et web-endpoint hvor logintjenestens certifikat og metadata kan hentes.</p>\n"
			+ "\n"
			+ "<h4>Sessionsstyring og single logout</h4>\n"
			+ "<p>Tjenesteudbyderen er ansvarlig for at sikre korrekt sessionsstyring i egen løsning, herunder at sessioner udløber efter en given tidsperiodes inaktivtet, samt at tjenesteudbyderens løsning understøtte Single Logout, så et logud initieret enten fra kommunens logintjeneste, eller fra tjenesteudbyderens løsning, videresendes til modparten, så brugeren er logget ud af begge systemer.</p>";
	
	@Autowired
	private TUTermsAndConditionsDao termsAndConditionsDao;

	@Autowired
	private CommonConfiguration commonConfiguration;
	
	public TUTermsAndConditions getTermsAndConditions() {
		List<TUTermsAndConditions> all = null;

		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			all = new ArrayList<>();
		}
		else {
			all = termsAndConditionsDao.findAll();
		}
		
		if (all.size() == 0) {
			TUTermsAndConditions termsAndConditions = new TUTermsAndConditions();
			termsAndConditions.setContent(fixedTerms);
		
			return termsAndConditions;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}
		
		log.error("More than one row with TU terms and agreements");
		
		return all.get(0);
	}

	public TUTermsAndConditions save(TUTermsAndConditions entity) {
		return termsAndConditionsDao.save(entity);
	}
}
