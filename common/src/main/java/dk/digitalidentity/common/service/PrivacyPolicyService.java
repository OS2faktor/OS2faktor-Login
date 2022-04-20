package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PrivacyPolicyDao;
import dk.digitalidentity.common.dao.model.PrivacyPolicy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PrivacyPolicyService {

	@Autowired
	private PrivacyPolicyDao privacyPolicyDao;

	public PrivacyPolicy getPrivacyPolicy() {
		List<PrivacyPolicy> all = privacyPolicyDao.findAll();
		
		if (all.isEmpty()) {
			var privacyPolicy = new PrivacyPolicy();
			privacyPolicy.setContent("<p>Produktet der anvendes til administration af din erhvervsidentitet hedder OS2faktor. OS2faktor behandler persondata, og har derfor udarbejdet denne privatlivspolitik, for at oplyse om hvordan disse data behandles. </p> <h3>Omfang af data der behandles</h3> <p>Der behandles følgende data</p> <ul> <li>Almindelige persondata</li> <li>Transaktionsdata</li> </ul> <h3>Anvendelse af data</h3> <p>Data indsamles alene for at understøtte funktionalitet i OS2faktor. Der registreres ingen unødige oplysninger, og data anvendes alene til at understøtte den forretningsmæssige anvendelse af OS2faktor applikationen.</p> <h3>Sletning af data</h3> <p>Data slettes når de ikke længere er i anvendelse.</p> <h3>Videregivelse af data</h3> <p>Der videregives ikke data til 3.part, med mindre dette er krævet af lovgivningen.</p> <h3>Dine rettigheder</h1> <p>Du har følgende rettigheder mht de data der er registreret om dig</p> <ul> <li>Få oplyst hvilke hvilke data vi har registreret</li> <li>Få rettet evt unøjagtige data</li> <li>Få slettet data</li> <li>Gøre indsigelse mod vores behandling af data</li> </ul> <h3>Kontaktoplysninger</h3> Digital Identity Aps<br /> Bakkedraget 1<br /> 8362 Hørning<br /> <a href=\"mailto:kontakt@digital-identity.dk\">kontakt@digital-identity.dk</a>");
		
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