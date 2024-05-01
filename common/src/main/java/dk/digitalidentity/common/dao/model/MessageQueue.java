package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

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
