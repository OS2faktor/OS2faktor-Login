package dk.digitalidentity.mvc.admin.dto;

import java.util.List;

import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailTemplateDTO {
	private long id;
	private List<EmailTemplateChildDTO> children;
	private EmailTemplateType emailTemplateType;
	private String templateTypeText;
	private boolean emailAllowed;
	private boolean eboksAllowed;
}
