package dk.digitalidentity.common.dao.model.mapping;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "session_temporary_client_mapping")
@Getter
@Setter
@NoArgsConstructor
public class TemporaryClientSessionMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne
	@JoinColumn(name = "temporary_client_id")
	@NotNull
	private TemporaryClientSessionKey temporaryClient;

	@NotNull
	@Column(name = "session_id")
	private String sessionId;

	public TemporaryClientSessionMapping(TemporaryClientSessionKey temporaryClient, String sessionId) {
		this.temporaryClient = temporaryClient;
		this.sessionId = sessionId;
	}
}
