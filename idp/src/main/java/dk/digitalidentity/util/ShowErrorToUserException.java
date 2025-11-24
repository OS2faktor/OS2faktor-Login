package dk.digitalidentity.util;

public class ShowErrorToUserException extends IdPFlowException {
	private static final long serialVersionUID = 1L;

	public ShowErrorToUserException(String errorMessage) {
		super(errorMessage);
	}

	public ShowErrorToUserException(String errorMessage, String errorCode, String cmsHelpMessageKey) {
		super(errorMessage, errorCode, cmsHelpMessageKey);
	}

	public ShowErrorToUserException(String errorMessage, Exception exception) {
		super(errorMessage, exception);
	}

	public ShowErrorToUserException(String errorMessage, String errorCode, String cmsHelpMessageKey, Exception exception) {
		super(errorMessage, errorCode, cmsHelpMessageKey, exception);
	}
}
