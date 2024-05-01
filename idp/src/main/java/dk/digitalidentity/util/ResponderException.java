package dk.digitalidentity.util;

public class ResponderException extends IdPFlowException {
	private static final long serialVersionUID = -5821217726301230309L;

    public ResponderException(String errorMessage) {
        super(errorMessage);
    }

    public ResponderException(String errorMessage, String errorCode, String helpMessage) {
        super(errorMessage, errorCode, helpMessage);
    }

    public ResponderException(String errorMessage, Exception exception) {
        super(errorMessage, exception);
    }
    
    public ResponderException(String errorMessage, String errorCode, String helpMessage, Exception exception) {
    	super(errorMessage, errorCode, helpMessage, exception);
    }
}
