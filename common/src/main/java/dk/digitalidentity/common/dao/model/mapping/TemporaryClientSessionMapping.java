package dk.digitalidentity.common.dao.model.mapping;

import com.fasterxml.jackson.annotation.JsonBackReference;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

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
