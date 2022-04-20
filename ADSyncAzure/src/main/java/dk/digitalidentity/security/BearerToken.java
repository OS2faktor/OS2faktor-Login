package dk.digitalidentity.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class BearerToken {
    private LocalDateTime expiryTimestamp;
    private String accessToken;

    public BearerToken(Map<String, String> body) throws IllegalArgumentException {
        if (body == null) {
            throw new IllegalArgumentException("Body cant be null");
        }

        if (!body.containsKey("access_token") || !body.containsKey("expires_in")) {
            throw new IllegalArgumentException("access_token or expires_in was null");
        }

        accessToken = body.get("access_token");

        String expires_in = body.get("expires_in");
        long expiresInSeconds = Long.parseLong(expires_in);
        expiryTimestamp = LocalDateTime.now().plusSeconds(expiresInSeconds);
    }
}
