package dk.digitalidentity.common.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class InlineImageDTO {
	private boolean base64;
	private String cid;
	private String src;
}
