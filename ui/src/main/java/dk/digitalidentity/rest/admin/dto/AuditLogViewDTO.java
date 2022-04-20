package dk.digitalidentity.rest.admin.dto;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.context.MessageSource;

import dk.digitalidentity.datatables.model.AuditLogView;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogViewDTO {
	private long id;
	private LocalDateTime tts;
	private String userId;
	private String personName;
	private String logAction;
	private String message;
	
	public AuditLogViewDTO(AuditLogView auditlog, MessageSource messageSource, Locale locale) {
		this.id = auditlog.getId();
		this.tts = auditlog.getTts();
		this.userId = auditlog.getUserId();
		this.personName = auditlog.getPersonName();
		this.logAction = messageSource.getMessage(auditlog.getLogAction().getMessage(), null, locale);
		this.message = auditlog.getMessage();
	}
}
