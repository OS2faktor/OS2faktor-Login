package dk.digitalidentity.common.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TransformInlineImageDTO {
	private List<InlineImageDTO> inlineImages;
	private String message;
}
