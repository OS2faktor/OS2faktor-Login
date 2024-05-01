package dk.digitalidentity.api.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KeystoreWithSwapDate {
	private String keystore;
	private String password;
	private LocalDateTime swapDate;
}
