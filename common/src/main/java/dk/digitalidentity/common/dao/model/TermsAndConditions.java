package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;

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
}
