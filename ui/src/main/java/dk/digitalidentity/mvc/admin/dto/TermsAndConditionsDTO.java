package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class TermsAndConditionsDTO {
	private String content;
	private boolean mustApprove;
}
