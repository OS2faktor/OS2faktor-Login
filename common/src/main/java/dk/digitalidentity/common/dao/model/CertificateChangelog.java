package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import dk.digitalidentity.common.dao.model.enums.CertificateChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "certificate_changelog")
@Setter
@Getter
public class CertificateChangelog {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column
	private LocalDateTime tts;
		
	@Column
	private String ipAddress;
	
	@Column
	private String operatorId;
	
	// action performed

	@Column
	@Enumerated(EnumType.STRING)
	private CertificateChange changeType;

	@Column
	private String details;

}
