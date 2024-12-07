package dk.digitalidentity.common.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@Entity
@Table(name = "email_template_children")
public class EmailTemplateChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@NotNull
	private String title;

	@Column
	@NotNull
	private String message;
	
	@Column
	private boolean enabled;

	@Column
	private boolean eboks;
	
	@Column
	private boolean email;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "domain_id")
	private Domain domain;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email_template_id")
	@NotNull
	private EmailTemplate emailTemplate;
}
