package dk.digitalidentity.common.service.mfa.model;

public enum ClientType {
	WINDOWS("enum.clienttype.windows"),
	IOS("enum.clienttype.ios"),
	ANDROID("enum.clienttype.android"),
	CHROME("enum.clienttype.chrome"),
	YUBIKEY("enum.clienttype.yubikey");

	private String message;
	
	private ClientType(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
}
