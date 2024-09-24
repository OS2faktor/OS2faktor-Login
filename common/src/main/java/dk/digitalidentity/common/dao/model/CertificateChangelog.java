package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import dk.digitalidentity.common.dao.model.enums.CertificateChange;
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
