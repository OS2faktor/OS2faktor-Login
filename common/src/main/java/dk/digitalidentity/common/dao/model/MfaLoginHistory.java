package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mfa_login_history")
@Setter
@Getter
public class MfaLoginHistory {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column
	private String serverName;

	@Column
	private String deviceId;

	@Column
	private String status;

	@Column
	private LocalDateTime createdTts;

	@Column
	private LocalDateTime pushTts;

	@Column
	private LocalDateTime fetchTts;

	@Column
	private LocalDateTime responseTts;

	@Column
	private String clientType;

	@Column
	private String systemName;
	
	@Column
	private String username;

}
