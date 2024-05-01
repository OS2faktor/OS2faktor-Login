package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.CreationTimestamp;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "temporary_client_session_keys")
@Setter
@Getter
@NoArgsConstructor
public class TemporaryClientSessionKey {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@CreationTimestamp
	@Column
	private LocalDateTime tts;

	@ManyToOne
	@JoinColumn(name = "person_id", nullable = false)
	private Person person;

	@Enumerated(EnumType.STRING)
	@Column
	@NotNull
	private NSISLevel nsisLevel;

	@Column
	private String ipAddress;

	@Column
	private String sessionKey;

	public TemporaryClientSessionKey(Person person, NSISLevel nsisLevel, String ipAddress) {
		this.person = person;
		this.nsisLevel = nsisLevel;
		this.ipAddress = ipAddress;

		this.tts = LocalDateTime.now();
		this.sessionKey = UUID.randomUUID().toString();
	}
}
