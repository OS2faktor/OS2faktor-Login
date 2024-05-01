package dk.digitalidentity.common.dao.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "oauth2_registered_client")
@Setter
@Getter
public class SqlOIDCServiceProviderConfig {

	@Id
	@Column
	private String id;

	@OneToOne(cascade= CascadeType.ALL)
	@JoinColumn(name="sql_service_provider_configuration_id")
	private SqlServiceProviderConfiguration sqlServiceProviderConfiguration;

	@Column
	@NotNull
	private String clientId;

	@Column
	@NotNull
	private Instant clientIdIssuedAt;

	@Column
	@NotNull
	private String clientSecret;

	@Column
	@NotNull
	private Instant clientSecretExpiresAt;

	@Column
	@NotNull
	private String clientName;


	//new ClientAuthenticationMethod("private_key_jwt");
	@Column
	@NotNull
	private String clientAuthenticationMethods;

	//new AuthorizationGrantType("authorization_code");
	@Column
	@NotNull
	private String authorizationGrantTypes;

	// CSV
	@Column
	@NotNull
	private String redirectUris;


	//CSV
	@Column
	@NotNull
	private String scopes;

	// Client Settings
	@Column
	@NotNull
	private boolean requireProofKey; // false

	@Column
	@NotNull
	private boolean requireAuthorizationConsent; // false

	@Column
	private String jwkSetUrl;

	@Column
	private String tokenEndpointAuthenticationSigningAlgorithmName;

	// TokenSettings
	@Column
	@NotNull
	private Duration accessTokenTimeToLive; // Duration.ofMinutes(5)

	@Column
	@NotNull
	private String accessTokenFormat; // OAuth2TokenFormat.SELF_CONTAINED

	@Column
	@NotNull
	private boolean reuseRefreshTokens; // true

	@Column
	@NotNull
	private Duration refreshTokenTimeToLive; // Duration.ofMinutes(60)

	@Column
	private String idTokenSignatureAlgorithmName; // SignatureAlgorithm.RS256
}


