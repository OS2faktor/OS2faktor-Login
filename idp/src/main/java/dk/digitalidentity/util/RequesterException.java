package dk.digitalidentity.util;

public class RequesterException extends Exception {
	private static final long serialVersionUID = 3390901416231030969L;

	public RequesterException(Exception e) {
        super(e.getMessage(), e);
    }

    public RequesterException(String errorMessage) {
        super(errorMessage);
    }

    public RequesterException(String errorMessage, Exception exception) {
        super(errorMessage, exception);
    }
}
