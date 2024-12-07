package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import dk.digitalidentity.common.dao.model.enums.MitIdErhvervAccountErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "mitid_erhverv_account_errors")
public class MitIdErhvervAccountError {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private Person person;

	@Column
	@NotNull
	@Enumerated(EnumType.STRING)
	private MitIdErhvervAccountErrorType errorType;

	@Column
	@CreationTimestamp
	private LocalDateTime tts;

	@Column
	private String nemloginUserUuid;

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		
		if (!(obj instanceof MitIdErhvervAccountError)) {
			return false;
		}

		MitIdErhvervAccountError objError = (MitIdErhvervAccountError) obj;
		
		if (objError.person == null) {
			return false;
		}
		
		if (objError.person.getId() == person.getId() &&
			objError.errorType == errorType &&
			Objects.equals(objError.nemloginUserUuid, nemloginUserUuid)) {
			
			return true;
		}
		
		return false;
	}
}
