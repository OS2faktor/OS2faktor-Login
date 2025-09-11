package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Audited
@Entity(name = "terms_and_conditions")
@Getter
@Setter
public class TermsAndConditions {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String content;

	@Column
	private LocalDateTime mustApproveTts;
	
	@Column
	@UpdateTimestamp
	private LocalDateTime lastUpdatedTts;
	
	@Transient
	private transient String fixedTerms =
		"<p>Jeg medgiver hermed at være indforstået med nedenstående vilkår for anvendelsen af erhvervsidentiteten</p><ul>" +
		"<li>At jeg ved aktiveringen af erhvervsidentiteten oplyser fyldestgørende og retvisende identifikationsinformationer</li>" +
		"<li>At jeg ikke deler erhvervsidentiteten med andre</li>" +
		"<li>At jeg holder kodeord og andre loginmidler tilknyttet erhvervsidentiteten fortrolig</li>" +
		"<li>At jeg omgående spærrer erhvervsidentiteten, eller at jeg skifter kodeord og andre loginmidler, ved mistanke om at erhvervsidentiteten er blevet kompromitteret</li>" +
		"<li>At jeg omgående anmoder om at få min erhvervsidentitet genudstedt hvis de tilknyttede identitets-data (fx personnummer) har ændret sig siden udstedelsen</li>" +
		"</ul>" +
		"<p>Jeg medgiver samtidig at jeg er bekendt med min arbejdsgivers informationssikkerhedspolitikker, og følger disse, og at jeg er ansvarlig for løbende at holde mig opdateret omkring ændringer i informationssikkerhedspolitikken.</p>" +
		"<p>Endeligt er jeg bekendt med at jeg kun må anvende erhvervsidentiteten i forbindelse med mit arbejdsmæssige hverv.</p>";
}
