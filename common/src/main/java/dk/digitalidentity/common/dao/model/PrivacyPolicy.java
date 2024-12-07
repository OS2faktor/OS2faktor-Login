package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Audited
@Entity(name = "privacy_policy")
@Getter
@Setter
public class PrivacyPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private String content;
	
	@Column(name = "last_updated_tts")
	@UpdateTimestamp
	private LocalDateTime lastUpdatedTts;
}
