package dk.digitalidentity.util;

public class ResponderException extends Exception {
	private static final long serialVersionUID = -5821217726301230309L;

	public ResponderException(Exception e) {
        super(e.getMessage(), e);;
    }

    public ResponderException(String errorMessage) {
        super(errorMessage);
    }

    public ResponderException(String errorMessage, Exception exception) {
        super(errorMessage, exception);
    }
}
