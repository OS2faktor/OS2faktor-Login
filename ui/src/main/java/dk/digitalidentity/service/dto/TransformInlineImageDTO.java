package dk.digitalidentity.service.dto;

import java.util.List;

import dk.digitalidentity.common.service.dto.InlineImageDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TransformInlineImageDTO {
	private List<InlineImageDTO> inlineImages;
	private String message;
}
