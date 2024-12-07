package dk.digitalidentity.common.dao.model;

import java.util.List;

import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "email_templates")
public class EmailTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column
	@Enumerated(EnumType.STRING)
	@NotNull
	private EmailTemplateType templateType;

	@OneToMany(mappedBy = "emailTemplate", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private List<EmailTemplateChild> children;
}
