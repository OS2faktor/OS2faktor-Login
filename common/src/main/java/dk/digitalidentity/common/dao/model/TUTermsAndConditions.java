package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Audited
@Entity(name = "tu_terms_and_conditions")
@Getter
@Setter
public class TUTermsAndConditions {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String content;
	
	@Column
	@UpdateTimestamp
	private LocalDateTime lastUpdatedTts;
}
