package dk.digitalidentity.config.modules;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AzureAd {
	private String baseUrl = "https://graph.microsoft.com/";
	private String loginBaseUrl = "https://login.microsoftonline.com/";
	private String apiVersion = "v1.0";
	
	private String clientID;
	private String clientSecret;
	private String tenantID;

	private String cprField;
	private String sAMAccountNameField = "userPrincipalName";
	private String upn;
	private String nsisAllowedGroupId;
	private Map<String, String> attributes;

	// For KOMBIT roles
	private String entityIdAndCvrField;
	private String nameField;
	private String municipalCvr;
	private String kombitRoleUrl;
	private String kombitRolesMainGroupId;
}