package dk.digitalidentity.common.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class MailConfiguration {
	private boolean enabled = false;
	private String from = "no-reply@os2faktor.dk";
	private String fromName = "OS2faktor";
	private String username;
	private String password;
	private String host;
}
