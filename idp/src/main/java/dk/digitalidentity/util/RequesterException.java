package dk.digitalidentity.util;

public class RequesterException extends IdPFlowException {
	private static final long serialVersionUID = 3390901416231030969L;

    public RequesterException(String errorMessage) {
        super(errorMessage);
    }
    
    public RequesterException(String errorMessage, String errorCode, String helpMessage) {
        super(errorMessage, errorCode, helpMessage);
    }

    public RequesterException(String errorMessage, Exception exception) {
        super(errorMessage, exception);
    }
    
    public RequesterException(String errorMessage, String errorCode, String helpMessage, Exception exception) {
    	super(errorMessage, errorCode, helpMessage, exception);
    }
}
