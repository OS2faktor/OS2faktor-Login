package dk.digitalidentity.datatables.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "view_mfa_login_history")
@Getter
@Setter
public class MfaLoginHistoryView {

	@Id
	@Column
	private long id;
	
	@Column
	private String deviceId;

	@Column
	private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Column
	private LocalDateTime createdTts;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Column
	private LocalDateTime pushTts;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Column
	private LocalDateTime fetchTts;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Column
	private LocalDateTime responseTts;

	@Column
	private String clientType;

	@Column
	private String systemName;

	@Column
	private String username;

}
