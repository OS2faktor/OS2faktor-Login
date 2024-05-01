package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.CreationTimestamp;

import dk.digitalidentity.common.dao.model.enums.MitIdErhvervAccountErrorType;
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
