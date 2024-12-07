package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "message_queue")
public class MessageQueue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@NotNull
	private String subject;

	@Column
	@NotNull
	private String message;
	
	@Column
	private String cpr;

	@Column
	private String email;
	
	@Column
	private long personId;

	@Column
	private LocalDateTime deliveryTts;
	
	@Column
	private boolean operatorApproved;
}
