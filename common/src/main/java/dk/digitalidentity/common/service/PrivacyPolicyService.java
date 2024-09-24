package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PrivacyPolicyDao;
import dk.digitalidentity.common.dao.model.PrivacyPolicy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PrivacyPolicyService {
	private static final String fixedTerms =
			"<p>Produktet der anvendes til administration af din erhvervsidentitet hedder OS2faktor. OS2faktor behandler persondata, og har derfor udarbejdet denne privatlivspolitik, for at oplyse om hvordan disse data behandles.</p>\n"
			+ "\n"
			+ "<h3>Omfang af data der behandles</h3>\n"
			+ "<p>Der behandles følgende data</p>\n"
			+ "\n"
			+ "<ul>\n"
			+ "<li>Almindelige persondata</li>\n"
			+ "<li>Personnummer</li>\n"
			+ "<li>Transaktionsdata</li>\n"
			+ "</ul>\n"
			+ "\n"
			+ "<h3>Anvendelse af data</h3>\n"
			+ "<p>Data indsamles alene for at understøtte funktionalitet i OS2faktor. Der registreres ingen unødige oplysninger, og data anvendes alene til at understøtte den forretningsmæssige anvendelse af OS2faktor applikationen.</p>\n"
			+ "\n"
			+ "<h3>Sletning af data</h3>\n"
			+ "<p>Data slettes når de ikke længere er i anvendelse.</p>\n"
			+ "\n"
			+ "<h3>Videregivelse af data</h3>\n"
			+ "<p>Der videregives ikke data til 3.part, med mindre dette er krævet af lovgivningen.</p>\n"
			+ "\n"
			+ "<h3>Dine rettigheder</h1>\n"
			+ "<p>Du har følgende rettigheder mht de data der er registreret om dig</p>\n"
			+ "\n"
			+ "<ul>\n"
			+ "<li>Få oplyst hvilke hvilke data vi har registreret</li>\n"
			+ "<li>Få rettet evt unøjagtige data</li>\n"
			+ "<li>Få slettet data</li>\n"
			+ "<li>Gøre indsigelse mod vores behandling af data</li>\n"
			+ "</ul>\n"
			+ "\n"
			+ "<h3>Kontakt til databehandleren</h3>\n"
			+ "<p>\n"
			+ "Digital Identity er databehandler, og kan kontaktes nedenfor\n"
			+ "</p>\n"
			+ "\n"
			+ "<p>\n"
			+ "Digital Identity<br>\n"
			+ "Gunnar Clausens Vej 68<br>\n"
			+ "8260 Viby J<br>\n"
			+ "Mail: kontakt@digital-identity.dk\n"
			+ "</p>";

	@Autowired
	private PrivacyPolicyDao privacyPolicyDao;

	@Autowired
	private CommonConfiguration commonConfiguration;

	public PrivacyPolicy getPrivacyPolicy() {
		List<PrivacyPolicy> all = null;
		
		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			all = new ArrayList<>();
		}
		else {
			all = privacyPolicyDao.findAll();
		}
		
		if (all.isEmpty()) {
			PrivacyPolicy privacyPolicy = new PrivacyPolicy();
			privacyPolicy.setContent(fixedTerms);
		
			return privacyPolicy;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}
		
		log.error("More than one row with privacy policy");
		
		return all.get(0);
	}

	public PrivacyPolicy save(PrivacyPolicy entity) {
		return privacyPolicyDao.save(entity);
	}
}
