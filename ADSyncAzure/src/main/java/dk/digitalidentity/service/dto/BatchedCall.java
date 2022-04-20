package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BatchedCall {
	private String url;
	private String method;
	private String id;

	public BatchedCall(String url, String method, String id) {
		this.url = url;
		this.method = method;
		this.id = id;
	}
}
